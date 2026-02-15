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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(LoggingGlobalFilter.class);
    private static final String ERROR_ATTRIBUTE = "LOG_ERROR_MSG";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 같은 X-Tx-Id가 gateway를 재진입할 때 hop 카운트 관리
    private final ConcurrentHashMap<String, AtomicInteger> hopCounter = new ConcurrentHashMap<>();

    private final LogStorageService storageService;
    private final KafkaMetadataSender metadataSender;

    public LoggingGlobalFilter(LogStorageService storageService, KafkaMetadataSender metadataSender) {
        this.storageService = storageService;
        this.metadataSender = metadataSender;
    }

    @NotNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String txId = exchange.getRequest().getHeaders().getFirst("X-Tx-Id");
        boolean isNewTx = (txId == null);
        if (isNewTx) txId = UUID.randomUUID().toString();

        // Hop 관리: 같은 X-Tx-Id가 gateway를 재진입하면 hop 자동 증가
        final String finalTxId = txId;
        final int finalHop = hopCounter
                .computeIfAbsent(finalTxId, k -> new AtomicInteger(0))
                .incrementAndGet();
        final String path = exchange.getRequest().getURI().getPath();

        // Request Header 직렬화 & 즉시 업로드
        byte[] reqHeaderBytes = serializeHeaders(exchange.getRequest().getHeaders());
        uploadData(finalTxId, reqHeaderBytes, "req.header", finalHop);

        // 요청 메타 정보 로깅
        String method = exchange.getRequest().getMethod().name();
        logger.info("[REQ] txId={}, hop={}, method={}, path={}", finalTxId, finalHop, method, path);

        // 메모리 버퍼 준비
        ByteArrayOutputStream reqOutputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream resOutputStream = new ByteArrayOutputStream();

        // Decorate (add X-Tx-Id header to downstream request)
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-Tx-Id", finalTxId)
                .build();
        RequestBufferLoggingDecorator reqDecorator = new RequestBufferLoggingDecorator(
                mutatedRequest, reqOutputStream, finalTxId, finalHop);
        ResponseBufferLoggingDecorator resDecorator = new ResponseBufferLoggingDecorator(exchange.getResponse(), resOutputStream);

        return chain.filter(exchange.mutate().request(reqDecorator).response(resDecorator).build())
                .doOnError(e -> exchange.getAttributes().put(ERROR_ATTRIBUTE, e.getMessage()))
                .doFinally(signalType -> {
                    // Response Header 직렬화 & 업로드
                    byte[] resHeaderBytes = serializeHeaders(exchange.getResponse().getHeaders());
                    uploadData(finalTxId, resOutputStream.toByteArray(), "res", finalHop);
                    uploadData(finalTxId, resHeaderBytes, "res.header", finalHop);

                    sendMetadata(exchange, finalTxId, finalHop, path, startTime);

                    if (isNewTx) {
                        hopCounter.remove(finalTxId);
                    }
                });
    }

    private byte[] serializeHeaders(HttpHeaders headers) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(headers.toSingleValueMap());
        } catch (Exception e) {
            logger.warn("Failed to serialize headers", e);
            return new byte[0];
        }
    }

    private void uploadData(String txId, byte[] data, String type, int hop) {
        try {
            storageService.upload(txId, data, type, hop);
        } catch (Exception e) {
            logger.warn("Failed to upload data: txId={}, type={}", txId, type, e);
        }
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

    // --- Request Decorator ---
    class RequestBufferLoggingDecorator extends ServerHttpRequestDecorator {
        private final ByteArrayOutputStream outputStream;
        private final String txId;
        private final int hop;

        public RequestBufferLoggingDecorator(ServerHttpRequest delegate, ByteArrayOutputStream outputStream,
                                             String txId, int hop) {
            super(delegate);
            this.outputStream = outputStream;
            this.txId = txId;
            this.hop = hop;
        }

        @NotNull
        @Override
        public Flux<DataBuffer> getBody() {
            return super.getBody().doOnNext(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                buffer.readPosition(buffer.readPosition() - bytes.length);
                outputStream.write(bytes, 0, bytes.length);
            }).doOnComplete(() -> {
                // 요청 바디 소비 완료 시 즉시 업로드
                uploadData(txId, outputStream.toByteArray(), "req", hop);
            });
        }
    }

    // --- Response Decorator ---
    class ResponseBufferLoggingDecorator extends ServerHttpResponseDecorator {
        private final ByteArrayOutputStream outputStream;

        public ResponseBufferLoggingDecorator(ServerHttpResponse delegate, ByteArrayOutputStream outputStream) {
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
