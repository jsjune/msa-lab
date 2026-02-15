package org.example.springcloudgatwaylab.controller;

import io.minio.GetObjectArgs;
import io.minio.MinioAsyncClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/logs")
@ConditionalOnProperty(name = "gateway.logs.storage.type", havingValue = "minio", matchIfMissing = true)
public class LogReaderController {

    private final MinioAsyncClient minioClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LogReaderController(@Value("${gateway.logs.minio.endpoint}") String endpoint,
                               @Value("${gateway.logs.minio.access-key}") String accessKey,
                               @Value("${gateway.logs.minio.secret-key}") String secretKey) {
        this.minioClient = MinioAsyncClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * bodyUrl(Kafka 메타데이터의 bodyUrl 값)으로 req/res body를 조회한다.
     *
     * GET /logs/body?bodyUrl=s3://gateway-logs/2026/02/15/{txId}
     *
     * bodyUrl 형식: s3://{bucket}/{datePath}/{txId}
     * → MinIO 오브젝트: {datePath}/{txId}.req, {datePath}/{txId}.res
     */
    @GetMapping("/body")
    public Mono<ResponseEntity<Map<String, Object>>> getLogByBodyUrl(@RequestParam String bodyUrl) {
        // s3://gateway-logs/2026/02/15/{txId} 파싱
        String withoutScheme = bodyUrl.replaceFirst("^s3://", "");
        int firstSlash = withoutScheme.indexOf('/');
        if (firstSlash < 0) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "invalid bodyUrl format")));
        }
        String bucket = withoutScheme.substring(0, firstSlash);
        String objectPrefix = withoutScheme.substring(firstSlash + 1);

        Mono<String> reqBody = fetchObject(bucket, objectPrefix + ".req");
        Mono<String> resBody = fetchObject(bucket, objectPrefix + ".res");

        return Mono.zip(reqBody, resBody)
                .map(tuple -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("request", parseJson(tuple.getT1()));
                    result.put("response", parseJson(tuple.getT2()));
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(result);
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }

    private Object parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, Object.class);
        } catch (Exception e) {
            return raw;
        }
    }

    private Mono<String> fetchObject(String bucket, String objectName) {
        return Mono.fromCallable(() -> minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectName)
                                .build()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::fromFuture)
                .flatMap(response -> Mono.fromCallable(() -> {
                    try (InputStream is = response) {
                        return new String(is.readAllBytes());
                    }
                }).subscribeOn(Schedulers.boundedElastic()));
    }
}
