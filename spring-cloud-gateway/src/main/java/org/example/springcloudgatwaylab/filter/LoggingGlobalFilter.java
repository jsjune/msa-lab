package org.example.springcloudgatwaylab.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springcloudgatwaylab.service.KafkaMetadataSender;
import org.example.springcloudgatwaylab.service.LogStorageService;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(LoggingGlobalFilter.class);
    private static final String ERROR_ATTRIBUTE = "LOG_ERROR_MSG";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String HOP_KEY_PREFIX = "hop:";
    private static final Duration HOP_KEY_TTL = Duration.ofMinutes(5);

    /**
     * Body가 없는 HTTP 메서드 집합.
     * 이 메서드들은 request body 업로드를 건너뛴다.
     */
    private static final Set<HttpMethod> BODY_LESS_METHODS = Set.of(
            HttpMethod.GET, HttpMethod.HEAD, HttpMethod.DELETE, HttpMethod.OPTIONS, HttpMethod.TRACE
    );

    static boolean hasBody(HttpMethod method) {
        return !BODY_LESS_METHODS.contains(method);
    }

    private final LogStorageService storageService;
    private final KafkaMetadataSender metadataSender;
    private final ReactiveStringRedisTemplate redisTemplate;

    public LoggingGlobalFilter(LogStorageService storageService,
                               KafkaMetadataSender metadataSender,
                               ReactiveStringRedisTemplate redisTemplate) {
        this.storageService = storageService;
        this.metadataSender = metadataSender;
        this.redisTemplate = redisTemplate;
    }

    @NotNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String txId = exchange.getRequest().getHeaders().getFirst("X-Tx-Id");
        boolean isNewTx = (txId == null);
        if (isNewTx) txId = UUID.randomUUID().toString();

        final String finalTxId = txId;
        final String hopKey = HOP_KEY_PREFIX + finalTxId;
        final String path = exchange.getRequest().getURI().getPath();
        final HttpMethod method = exchange.getRequest().getMethod();

        // Redis INCR로 hop 원자적 증가 → 이후 필터 체인 실행
        return redisTemplate.opsForValue().increment(hopKey)
                .flatMap(hopLong ->
                        // TTL 설정 (키 누수 방지)
                        redisTemplate.expire(hopKey, HOP_KEY_TTL).thenReturn(hopLong)
                )
                .flatMap(hopLong -> {
                    final int finalHop = hopLong.intValue();

                    // ── [1] Request Header 업로드 (논블로킹) ──
                    byte[] reqHeaderBytes = serializeHeaders(exchange.getRequest().getHeaders());
                    Mono<Void> reqHeaderUpload = uploadDataAsync(finalTxId, reqHeaderBytes, "req.header", finalHop);

                    logger.info("[REQ] txId={}, hop={}, method={}, path={}", finalTxId, finalHop, method, path);

                    // ── [2] Body 버퍼 준비 ──
                    ByteArrayOutputStream reqOutputStream = new ByteArrayOutputStream();
                    ByteArrayOutputStream resOutputStream = new ByteArrayOutputStream();

                    // ── [3] Decorator 구성 ──
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header("X-Tx-Id", finalTxId)
                            .build();

                    boolean hasBody = hasBody(method);

                    ServerHttpRequest decoratedRequest = hasBody
                            ? new RequestBufferLoggingDecorator(mutatedRequest, reqOutputStream)
                            : mutatedRequest;

                    ResponseBufferLoggingDecorator resDecorator = new ResponseBufferLoggingDecorator(
                            exchange.getResponse(), resOutputStream);

                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(decoratedRequest)
                            .response(resDecorator)
                            .build();

                    // ── [4] 필터 체인 실행 ──
                    return reqHeaderUpload
                            .then(chain.filter(mutatedExchange))
                            .doOnError(e -> exchange.getAttributes().put(ERROR_ATTRIBUTE, e.getMessage()))
                            .then(Mono.defer(() -> {
                                // ── [5] 응답 완료 후: 모든 업로드 + 메타데이터 전송 (논블로킹, 순서 보장) ──
                                Mono<Void> reqBodyUpload = hasBody
                                        ? uploadDataAsync(finalTxId, reqOutputStream.toByteArray(), "req", finalHop)
                                        : Mono.empty();

                                Mono<Void> resBodyUpload = uploadDataAsync(
                                        finalTxId, resOutputStream.toByteArray(), "res", finalHop);

                                byte[] resHeaderBytes = serializeHeaders(exchange.getResponse().getHeaders());
                                Mono<Void> resHeaderUpload = uploadDataAsync(
                                        finalTxId, resHeaderBytes, "res.header", finalHop);

                                Mono<Void> metadataUpload = Mono.fromRunnable(
                                                () -> sendMetadata(exchange, finalTxId, finalHop, path, startTime))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .then();

                                // 업로드 병렬 실행 → 완료 후 메타데이터 전송
                                return Mono.when(reqBodyUpload, resBodyUpload, resHeaderUpload)
                                        .then(metadataUpload);
                            }))
                            .then(Mono.defer(() -> {
                                // ── [6] 최초 진입 txId인 경우 Redis 키 정리 ──
                                // 위의 업로드/메타데이터가 모두 완료된 후에만 실행됨
                                if (isNewTx) {
                                    return redisTemplate.delete(hopKey).then();
                                }
                                return Mono.empty();
                            }));
                });
    }

    // ──────────────── 유틸리티 메서드 ────────────────

    static byte[] serializeHeaders(HttpHeaders headers) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(headers.toSingleValueMap());
        } catch (Exception e) {
            logger.warn("Failed to serialize headers", e);
            return new byte[0];
        }
    }

    /**
     * 논블로킹 업로드: boundedElastic 스케줄러에서 실행.
     * Netty 이벤트 루프 스레드를 차단하지 않는다.
     */
    private Mono<Void> uploadDataAsync(String txId, byte[] data, String type, int hop) {
        if (data == null || data.length == 0) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> {
                    try {
                        storageService.upload(txId, data, type, hop);
                    } catch (Exception e) {
                        logger.warn("Failed to upload data: txId={}, type={}", txId, type, e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void sendMetadata(ServerWebExchange exchange, String txId, int hop, String path, long startTime) {
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        URI targetUrl = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        String errorMsg = exchange.getAttribute(ERROR_ATTRIBUTE);

        boolean isError = (statusCode != null && statusCode.isError()) || errorMsg != null;
        String bodyUrl = storageService.getStorageBaseUrl(txId, hop);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("txId", txId);
        metadata.put("hop", hop);
        metadata.put("path", path);
        metadata.put("target", targetUrl != null ? targetUrl.toString() : "");
        metadata.put("duration", duration + "ms");
        metadata.put("status", statusCode != null ? statusCode.value() : 0);
        metadata.put("reqTime", Instant.ofEpochMilli(startTime).toString());
        metadata.put("resTime", Instant.ofEpochMilli(endTime).toString());
        metadata.put("bodyUrl", bodyUrl);

        if (isError) {
            metadata.put("error", errorMsg != null ? errorMsg : "HTTP Error");
        }

        logger.info("[RES] {}", metadata);
        metadataSender.send(metadata);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    // ──────────────── Request Decorator ────────────────

    /**
     * Request body를 버퍼에 복사만 한다.
     * 업로드는 외부(필터 체인 완료 후)에서 일괄 처리한다.
     */
    class RequestBufferLoggingDecorator extends ServerHttpRequestDecorator {
        private final ByteArrayOutputStream outputStream;

        public RequestBufferLoggingDecorator(ServerHttpRequest delegate,
                                             ByteArrayOutputStream outputStream) {
            super(delegate);
            this.outputStream = outputStream;
        }

        @NotNull
        @Override
        public Flux<DataBuffer> getBody() {
            return super.getBody().doOnNext(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                buffer.readPosition(buffer.readPosition() - bytes.length);
                outputStream.write(bytes, 0, bytes.length);
            });
            // doOnComplete에서 업로드하지 않음 → 필터 체인 완료 후 일괄 업로드
        }
    }

    // ──────────────── Response Decorator ────────────────

    class ResponseBufferLoggingDecorator extends ServerHttpResponseDecorator {
        private final ByteArrayOutputStream outputStream;

        public ResponseBufferLoggingDecorator(ServerHttpResponse delegate,
                                              ByteArrayOutputStream outputStream) {
            super(delegate);
            this.outputStream = outputStream;
        }

        @NotNull
        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return super.writeWith(Flux.from(body).doOnNext(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                buffer.readPosition(buffer.readPosition() - bytes.length);
                outputStream.write(bytes, 0, bytes.length);
            }));
        }
    }
}