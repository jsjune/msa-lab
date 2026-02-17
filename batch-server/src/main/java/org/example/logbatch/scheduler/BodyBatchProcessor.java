package org.example.logbatch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.logbatch.config.BatchProperties;
import org.example.logbatch.domain.GatewayLog;
import org.example.logbatch.domain.GatewayLogBody;
import org.example.logbatch.domain.LogEntryMapper;
import org.example.logbatch.repository.GatewayLogBodyRepository;
import org.example.logbatch.repository.GatewayLogRepository;
import org.example.logbatch.service.BodyCollectionService;
import org.example.logbatch.storage.MinioLogFetcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "batch.body", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BodyBatchProcessor {

    private final GatewayLogRepository gatewayLogRepository;
    private final GatewayLogBodyRepository gatewayLogBodyRepository;
    private final BodyCollectionService bodyCollectionService;
    private final MinioLogFetcher minioLogFetcher;
    private final BatchProperties batchProperties;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * DB에서 바디 미수집 로그를 조회하여 MinIO에서 바디를 가져와 저장한다.
     * Kafka 메타데이터 파이프라인과 완전히 독립적으로 동작한다.
     */
    @Scheduled(fixedDelayString = "${batch.body.fixed-delay:30000}")
    public void processBodyBatch() {
        if (!running.compareAndSet(false, true)) {
            log.info("Previous body batch still running, skipping this execution");
            return;
        }

        try {
            bodyCollectionService.refreshPolicyCache();

            int batchSize = batchProperties.getBody().getBatchSize();
            List<GatewayLog> logsNeedingBody =
                    gatewayLogRepository.findLogsNeedingBodyCollection(PageRequest.of(0, batchSize));

            if (logsNeedingBody.isEmpty()) {
                return;
            }

            int collected = 0;
            int skipped = 0;
            int failed = 0;

            for (GatewayLog gatewayLog : logsNeedingBody) {
                ProcessBodyResult result = processBody(gatewayLog);
                switch (result) {
                    case COLLECTED -> collected++;
                    case SKIPPED -> skipped++;
                    case MINIO_FAILED -> failed++;
                }
            }

            log.info("Body batch processed: candidates={}, collected={}, skipped={}, failed={}",
                    logsNeedingBody.size(), collected, skipped, failed);
        } catch (Exception e) {
            log.error("Body batch processing failed", e);
        } finally {
            bodyCollectionService.clearPolicyCache();
            running.set(false);
        }
    }

    private enum ProcessBodyResult {
        COLLECTED, SKIPPED, MINIO_FAILED
    }

    private ProcessBodyResult processBody(GatewayLog gatewayLog) {
        try {
            if (!bodyCollectionService.shouldCollectBody(gatewayLog.getPath())) {
                return ProcessBodyResult.SKIPPED;
            }

            MinioLogFetcher.FetchResult fetchResult =
                    minioLogFetcher.fetchAllByBodyUrl(gatewayLog.getBodyUrl());

            if (LogEntryMapper.isAllNull(fetchResult.requestBody(), fetchResult.responseBody(),
                    fetchResult.requestHeaders(), fetchResult.responseHeaders())) {
                return ProcessBodyResult.MINIO_FAILED;
            }

            GatewayLogBody toSave = GatewayLogBody.builder()
                    .gatewayLog(gatewayLog)
                    .requestBody(fetchResult.requestBody())
                    .responseBody(fetchResult.responseBody())
                    .requestHeaders(fetchResult.requestHeaders())
                    .responseHeaders(fetchResult.responseHeaders())
                    .build();
            gatewayLogBodyRepository.save(toSave);
            log.debug("Saved body for txId={}, hop={}", gatewayLog.getTxId(), gatewayLog.getHop());
            return ProcessBodyResult.COLLECTED;
        } catch (Exception e) {
            log.warn("Failed to fetch/save body for txId={}, hop={}: {}",
                    gatewayLog.getTxId(), gatewayLog.getHop(), e.getMessage());
            return ProcessBodyResult.MINIO_FAILED;
        }
    }

    public boolean isRunning() {
        return running.get();
    }
}
