package org.example.logbatch.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.logbatch.domain.BodyUrlParser;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioLogFetcher {

    private final MinioClient minioClient;

    public String fetchObject(String bucket, String objectKey) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build())) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                log.debug("MinIO object not found: {}/{}", bucket, objectKey);
            } else {
                log.warn("MinIO error fetching {}/{}: {}", bucket, objectKey, e.getMessage());
            }
            return null;
        } catch (Exception e) {
            log.warn("MinIO connection error fetching {}/{}: {}", bucket, objectKey, e.getMessage());
            return null;
        }
    }

    public FetchResult fetchAllByBodyUrl(String bodyUrl) {
        if (bodyUrl == null || bodyUrl.isBlank()) {
            return FetchResult.EMPTY;
        }

        BodyUrlParser.Result parsed = BodyUrlParser.parse(bodyUrl);
        if (parsed == null) {
            return FetchResult.EMPTY;
        }

        String bucket = parsed.getBucket();
        List<String> objectKeys = parsed.getObjectKeys();

        // objectKeys order: .req, .res, .req.header, .res.header (defined by BodyUrlParser.SUFFIXES)
        String req = fetchObject(bucket, objectKeys.get(0));
        String res = fetchObject(bucket, objectKeys.get(1));
        String reqHeader = fetchObject(bucket, objectKeys.get(2));
        String resHeader = fetchObject(bucket, objectKeys.get(3));

        return new FetchResult(req, res, reqHeader, resHeader);
    }

    public record FetchResult(
            String requestBody,
            String responseBody,
            String requestHeaders,
            String responseHeaders
    ) {
        public static final FetchResult EMPTY = new FetchResult(null, null, null, null);
    }
}
