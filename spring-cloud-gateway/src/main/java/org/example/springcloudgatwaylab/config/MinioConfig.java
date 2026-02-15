package org.example.springcloudgatwaylab.config;

import io.minio.MinioAsyncClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "gateway.logs.storage.type", havingValue = "minio", matchIfMissing = true)
public class MinioConfig {

    @Bean
    public MinioAsyncClient minioAsyncClient(
            @Value("${gateway.logs.minio.endpoint}") String endpoint,
            @Value("${gateway.logs.minio.access-key}") String accessKey,
            @Value("${gateway.logs.minio.secret-key}") String secretKey) {
        return MinioAsyncClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
