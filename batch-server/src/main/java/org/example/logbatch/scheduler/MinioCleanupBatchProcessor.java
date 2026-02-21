package org.example.logbatch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.example.logbatch.config.BatchProperties;
import org.example.logbatch.domain.GatewayLog;
import org.example.logbatch.repository.GatewayLogRepository;
import org.example.logbatch.storage.MinioObjectCleaner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "batch.body", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MinioCleanupBatchProcessor {

    private final GatewayLogRepository gatewayLogRepository;
    private final MinioObjectCleaner minioObjectCleaner;
    private final BatchProperties batchProperties;

    /**
     * body 수집 완료 레코드와 maxRetries 초과 레코드의 MinIO 객체를 정리한다.
     * - 수집 완료: MinIO 삭제 성공 시에만 bodyUrl=null로 업데이트 (실패 시 다음 배치에서 재시도)
     * - maxRetries 초과: MinIO 삭제 시도 후 결과와 무관하게 bodyUrl=null로 업데이트 (영구 포기)
     */
    @Scheduled(fixedDelayString = "${batch.body.cleanup-delay:3600000}")
    @SchedulerLock(name = "minioCleanupBatch", lockAtMostFor = "PT30M")
    public void processCleanupBatch() {
        try {
            int batchSize = batchProperties.getBody().getBatchSize();
            int maxRetries = batchProperties.getBody().getMaxRetries();

            int cleanedCollected = cleanupCollectedLogs(batchSize);
            int cleanedExceeded = cleanupExceededLogs(maxRetries, batchSize);

            log.info("MinIO cleanup batch: cleaned={} collected, {} exceeded-retries",
                    cleanedCollected, cleanedExceeded);
        } catch (Exception e) {
            log.error("MinIO cleanup batch failed", e);
        }
    }

    private int cleanupCollectedLogs(int batchSize) {
        List<GatewayLog> collectedLogs =
                gatewayLogRepository.findLogsWithBodyCollected(PageRequest.of(0, batchSize));
        int cleaned = 0;
        for (GatewayLog gatewayLog : collectedLogs) {
            try {
                boolean deleted = minioObjectCleaner.deleteAllByBodyUrl(gatewayLog.getBodyUrl());
                if (deleted) {
                    gatewayLog.clearBodyUrl();
                    gatewayLogRepository.save(gatewayLog);
                    cleaned++;
                }
            } catch (Exception e) {
                log.warn("Failed during collected log cleanup for txId={}: {}",
                        gatewayLog.getTxId(), e.getMessage());
            }
        }
        return cleaned;
    }

    private int cleanupExceededLogs(int maxRetries, int batchSize) {
        List<GatewayLog> exceededLogs =
                gatewayLogRepository.findLogsExceedingRetries(maxRetries, PageRequest.of(0, batchSize));
        int cleaned = 0;
        for (GatewayLog gatewayLog : exceededLogs) {
            try {
                minioObjectCleaner.deleteAllByBodyUrl(gatewayLog.getBodyUrl()); // fire-and-forget
            } catch (Exception e) {
                log.warn("Failed during exceeded log cleanup for txId={}: {}",
                        gatewayLog.getTxId(), e.getMessage());
            }
            gatewayLog.clearBodyUrl();
            gatewayLogRepository.save(gatewayLog);
            cleaned++;
        }
        return cleaned;
    }
}
