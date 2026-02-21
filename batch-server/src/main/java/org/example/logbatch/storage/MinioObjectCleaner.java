package org.example.logbatch.storage;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.logbatch.domain.BodyUrlParser;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioObjectCleaner {

    private final MinioClient minioClient;

    /**
     * bodyUrl에 해당하는 MinIO 객체 4개(.req, .res, .req.header, .res.header)를 삭제한다.
     * 삭제 실패(객체 없음 포함) 시 경고 로그만 기록하고 계속 진행한다.
     *
     * @return true if all 4 objects deleted successfully, false if any deletion failed
     */
    public boolean deleteAllByBodyUrl(String bodyUrl) {
        BodyUrlParser.Result parsed = BodyUrlParser.parse(bodyUrl);
        if (parsed == null) {
            log.warn("Cannot parse bodyUrl for cleanup: {}", bodyUrl);
            return false;
        }

        boolean allSuccess = true;
        for (String objectKey : parsed.getObjectKeys()) {
            try {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(parsed.getBucket())
                                .object(objectKey)
                                .build());
                log.debug("Deleted MinIO object: {}/{}", parsed.getBucket(), objectKey);
            } catch (Exception e) {
                log.warn("Failed to delete MinIO object {}/{}: {}", parsed.getBucket(), objectKey, e.getMessage());
                allSuccess = false;
            }
        }
        return allSuccess;
    }
}
