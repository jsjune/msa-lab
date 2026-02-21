package org.example.springcloudgatwaylab.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springcloudgatwaylab.service.KafkaMetadataSender;
import org.example.springcloudgatwaylab.service.LogStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Component
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(LoggingGlobalFilter.class);
    private static final String ERROR_ATTRIBUTE = "LOG_ERROR_MSG";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter KST_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneId.of("Asia/Seoul"));
    private static final Set<HttpMethod> BODY_LESS_METHODS = Set.of(
            HttpMethod.GET, HttpMethod.HEAD, HttpMethod.DELETE, HttpMethod.OPTIONS, HttpMethod.TRACE);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    static boolean hasBody(HttpMethod method) {
        return !BODY_LESS_METHODS.contains(method);
    }

    private final LogStorageService storageService;
    private final KafkaMetadataSender metadataSender;
    private final HopTracker hopTracker;
    final int maxBodySizeBytes;
    private final List<String> skipPaths;

    public LoggingGlobalFilter(LogStorageService storageService,
                               KafkaMetadataSender metadataSender,
                               HopTracker hopTracker,
                               @Value("${gateway.logs.max-body-size-bytes:1048576}") int maxBodySizeBytes,
                               @Value("${gateway.logs.skip-paths:/actuator/**}") String skipPathsConfig) {
        this.storageService = storageService;
        this.metadataSender = metadataSender;
        this.hopTracker = hopTracker;
        this.maxBodySizeBytes = maxBodySizeBytes;
        this.skipPaths = Arrays.stream(skipPathsConfig.split(","))
                .map(String::trim).filter(p -> !p.isEmpty()).collect(Collectors.toList());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        final String path = exchange.getRequest().getURI().getPath();
        if (skipPaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path))) {
            return chain.filter(exchange);
        }

        long startTime = System.currentTimeMillis();
        String txId = exchange.getRequest().getHeaders().getFirst("X-Tx-Id");
        boolean isNewTx = (txId == null);
        if (isNewTx) txId = UUID.randomUUID().toString();
        final String finalTxId = txId;
        final HttpMethod method = exchange.getRequest().getMethod();

        return hopTracker.increment(finalTxId).flatMap(result -> {
            final int hop = result.hop();
            final boolean redisErr = result.redisError();

            byte[] reqHeaderBytes = serializeHeaders(exchange.getRequest().getHeaders());
            logger.info("[REQ] txId={}, hop={}, method={}, path={}", finalTxId, hop, method, path);

            ByteArrayOutputStream reqStream = new ByteArrayOutputStream();
            ByteArrayOutputStream resStream = new ByteArrayOutputStream();
            AtomicBoolean reqTruncated = new AtomicBoolean(false);
            AtomicBoolean resTruncated = new AtomicBoolean(false);

            ServerHttpRequest decoratedReq = buildDecoratedRequest(exchange, finalTxId, method, reqStream, reqTruncated);
            BodyCapturingResponseDecorator resDecorator =
                    new BodyCapturingResponseDecorator(exchange.getResponse(), resStream, resTruncated, maxBodySizeBytes);
            ServerWebExchange mutated = exchange.mutate().request(decoratedReq).response(resDecorator).build();

            return uploadDataAsyncTracked(finalTxId, reqHeaderBytes, "req.header", hop).then()
                    .then(chain.filter(mutated))
                    .doOnError(e -> exchange.getAttributes().put(ERROR_ATTRIBUTE, e.getMessage()))
                    .then(Mono.defer(() -> uploadBodiesAndMetadata(
                            exchange, finalTxId, hop, path, startTime, redisErr,
                            method, reqStream, resStream, reqTruncated, resTruncated)))
                    .then(Mono.defer(() -> isNewTx ? hopTracker.delete(finalTxId) : Mono.empty()));
        });
    }

    private ServerHttpRequest buildDecoratedRequest(ServerWebExchange exchange, String txId,
            HttpMethod method, ByteArrayOutputStream reqStream, AtomicBoolean reqTruncated) {
        ServerHttpRequest mutated = exchange.getRequest().mutate().header("X-Tx-Id", txId).build();
        return hasBody(method)
                ? new BodyCapturingRequestDecorator(mutated, reqStream, reqTruncated, maxBodySizeBytes)
                : mutated;
    }

    private Mono<Void> uploadBodiesAndMetadata(ServerWebExchange exchange, String txId, int hop,
            String path, long startTime, boolean redisErr, HttpMethod method,
            ByteArrayOutputStream reqStream, ByteArrayOutputStream resStream,
            AtomicBoolean reqTruncated, AtomicBoolean resTruncated) {
        Mono<Boolean> reqBody = hasBody(method)
                ? uploadDataAsyncTracked(txId, reqStream.toByteArray(), "req", hop)
                : Mono.just(false);
        Mono<Boolean> resBody = uploadDataAsyncTracked(txId, resStream.toByteArray(), "res", hop);
        Mono<Boolean> resHeader = uploadDataAsyncTracked(
                txId, serializeHeaders(exchange.getResponse().getHeaders()), "res.header", hop);

        return Mono.zip(reqBody, resBody, resHeader).flatMap(results -> {
            boolean anyUploaded = results.getT1() || results.getT2() || results.getT3();
            return Mono.fromRunnable(() -> {
                try {
                    sendMetadata(exchange, txId, hop, path, startTime,
                            reqTruncated.get(), resTruncated.get(), redisErr, anyUploaded);
                } catch (Exception e) {
                    logger.warn("Failed to send metadata: txId={}", txId, e);
                }
            }).subscribeOn(Schedulers.boundedElastic()).then();
        });
    }

    private Mono<Boolean> uploadDataAsyncTracked(String txId, byte[] data, String type, int hop) {
        if (data == null || data.length == 0) return Mono.just(false);
        return Mono.<Boolean>fromCallable(() -> {
            try {
                storageService.upload(txId, data, type, hop);
                return true;
            } catch (Exception e) {
                logger.warn("Failed to upload data: txId={}, type={}", txId, type, e);
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    static byte[] serializeHeaders(HttpHeaders headers) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(headers.toSingleValueMap());
        } catch (Exception e) {
            logger.warn("Failed to serialize headers", e);
            return new byte[0];
        }
    }

    private void sendMetadata(ServerWebExchange exchange, String txId, int hop, String path, long startTime,
                              boolean reqBodyTruncated, boolean resBodyTruncated,
                              boolean redisError, boolean uploadSucceeded) {
        long endTime = System.currentTimeMillis();
        URI targetUrl = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        String errorMsg = exchange.getAttribute(ERROR_ATTRIBUTE);
        String bodyUrl = uploadSucceeded ? storageService.getStorageBaseUrl(txId, hop) : null;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("txId", txId);
        metadata.put("hop", hop);
        metadata.put("path", path);
        metadata.put("target", targetUrl != null ? targetUrl.toString() : "");
        metadata.put("duration", (endTime - startTime) + "ms");
        metadata.put("status", statusCode != null ? statusCode.value() : 0);
        metadata.put("reqTime", KST_FORMATTER.format(Instant.ofEpochMilli(startTime)));
        metadata.put("resTime", KST_FORMATTER.format(Instant.ofEpochMilli(endTime)));
        metadata.put("bodyUrl", bodyUrl);
        if ((statusCode != null && statusCode.isError()) || errorMsg != null)
            metadata.put("error", errorMsg != null ? errorMsg : "HTTP Error");
        if (reqBodyTruncated) metadata.put("reqBodyTruncated", true);
        if (resBodyTruncated) metadata.put("resBodyTruncated", true);
        if (redisError) metadata.put("redisError", true);

        logger.info("[RES] {}", metadata);
        metadataSender.send(metadata);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
