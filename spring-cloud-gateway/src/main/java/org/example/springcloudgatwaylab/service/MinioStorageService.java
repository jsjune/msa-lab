package org.example.springcloudgatwaylab.service;

import io.minio.MinioAsyncClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@ConditionalOnProperty(name = "gateway.logs.storage.type", havingValue = "minio", matchIfMissing = true)
public class MinioStorageService implements LogStorageService {

    private static final Logger logger = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioAsyncClient minioClient;
    private final String bucketName;

    public MinioStorageService(@Value("${gateway.logs.minio.endpoint}") String endpoint,
                               @Value("${gateway.logs.minio.access-key}") String accessKey,
                               @Value("${gateway.logs.minio.secret-key}") String secretKey,
                               @Value("${gateway.logs.storage.bucket}") String bucketName) {
        this.bucketName = bucketName;
        this.minioClient = MinioAsyncClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Override
    public void upload(String txId, byte[] data, String type, int hop) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectName = String.format("%s/%s-hop%d.%s", datePath, txId, hop, type);

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(data), data.length, -1)
                            .contentType("application/octet-stream")
                            .build()
            ).exceptionally(e -> {
                logger.error("Failed to upload log to MinIO: {}", objectName, e);
                return null;
            });
        } catch (Exception e) {
            logger.error("Error initiating MinIO upload for {}", objectName, e);
        }
    }

    @Override
    public String getStorageBaseUrl(String txId, int hop) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("s3://%s/%s/%s-hop%d", bucketName, datePath, txId, hop);
    }
}
