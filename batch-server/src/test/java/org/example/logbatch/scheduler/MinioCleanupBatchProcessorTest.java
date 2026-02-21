package org.example.logbatch.scheduler;

import org.example.logbatch.config.BatchProperties;
import org.example.logbatch.domain.GatewayLog;
import org.example.logbatch.repository.GatewayLogRepository;
import org.example.logbatch.storage.MinioObjectCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MinioCleanupBatchProcessor - MinIO 객체 정리 배치")
class MinioCleanupBatchProcessorTest {

    @Mock
    private GatewayLogRepository gatewayLogRepository;

    @Mock
    private MinioObjectCleaner minioObjectCleaner;

    private MinioCleanupBatchProcessor processor;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 3;

    @BeforeEach
    void setUp() {
        BatchProperties batchProperties = new BatchProperties(
                new BatchProperties.MetadataProperties(true, "gateway-meta-logs"),
                new BatchProperties.BodyProperties(true, 30000, BATCH_SIZE, MAX_RETRIES, 3_600_000L));
        processor = new MinioCleanupBatchProcessor(
                gatewayLogRepository, minioObjectCleaner, batchProperties);
    }

    private GatewayLog createLog(String txId, String bodyUrl) {
        return GatewayLog.builder()
                .id(1L)
                .txId(txId)
                .hop(1)
                .path("/server-a/hello")
                .bodyUrl(bodyUrl)
                .partitionDay(17)
                .build();
    }

    // ── 수집 완료 레코드 정리 ──

    @Test
    @DisplayName("body 수집 완료 레코드 → MinIO 삭제 호출 + bodyUrl=null 업데이트")
    void processCleanupBatch_collectedLog_deletesAndClearsBodyUrl() {
        String originalBodyUrl = "s3://gateway-logs/2026/02/17/tx-1-hop1";
        GatewayLog log = createLog("tx-1", originalBodyUrl);
        when(gatewayLogRepository.findLogsWithBodyCollected(PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(List.of(log));
        when(gatewayLogRepository.findLogsExceedingRetries(MAX_RETRIES, PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(Collections.emptyList());
        when(minioObjectCleaner.deleteAllByBodyUrl(originalBodyUrl)).thenReturn(true);

        processor.processCleanupBatch();

        verify(minioObjectCleaner).deleteAllByBodyUrl(originalBodyUrl);
        verify(gatewayLogRepository).save(log);
        assertThat(log.getBodyUrl()).isNull();
    }

    @Test
    @DisplayName("MinIO 삭제 실패 시 bodyUrl 업데이트 없음 — 다음 배치에서 재시도")
    void processCleanupBatch_minioDeleteFails_bodyUrlNotCleared() {
        GatewayLog log = createLog("tx-1", "s3://gateway-logs/2026/02/17/tx-1-hop1");
        when(gatewayLogRepository.findLogsWithBodyCollected(PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(List.of(log));
        when(gatewayLogRepository.findLogsExceedingRetries(MAX_RETRIES, PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(Collections.emptyList());
        when(minioObjectCleaner.deleteAllByBodyUrl(anyString())).thenReturn(false);

        processor.processCleanupBatch();

        verify(gatewayLogRepository, never()).save(any(GatewayLog.class));
        assertThat(log.getBodyUrl()).isNotNull();
    }

    // ── maxRetries 초과 레코드 정리 ──

    @Test
    @DisplayName("maxRetries 초과 레코드 → MinIO 삭제 시도 + 결과 무관하게 bodyUrl=null 업데이트")
    void processCleanupBatch_exceededLog_alwaysClearsBodyUrl() {
        String originalBodyUrl = "s3://gateway-logs/2026/02/17/tx-2-hop1";
        GatewayLog log = createLog("tx-2", originalBodyUrl);
        when(gatewayLogRepository.findLogsWithBodyCollected(PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(Collections.emptyList());
        when(gatewayLogRepository.findLogsExceedingRetries(MAX_RETRIES, PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(List.of(log));

        processor.processCleanupBatch();

        verify(minioObjectCleaner).deleteAllByBodyUrl(originalBodyUrl);
        verify(gatewayLogRepository).save(log);
        assertThat(log.getBodyUrl()).isNull();
    }

    @Test
    @DisplayName("MinIO 삭제 예외 발생 시 예외 전파 없음 (fire-and-forget)")
    void processCleanupBatch_minioThrowsException_noExceptionPropagated() {
        GatewayLog log = createLog("tx-3", "s3://gateway-logs/2026/02/17/tx-3-hop1");
        when(gatewayLogRepository.findLogsWithBodyCollected(PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(List.of(log));
        when(gatewayLogRepository.findLogsExceedingRetries(MAX_RETRIES, PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(Collections.emptyList());
        when(minioObjectCleaner.deleteAllByBodyUrl(anyString()))
                .thenThrow(new RuntimeException("MinIO connection refused"));

        // 예외가 전파되면 테스트 실패 — processCleanupBatch()가 예외를 삼켜야 함
        processor.processCleanupBatch();
    }

    @Test
    @DisplayName("정리 대상 없음 → MinIO 호출 없음")
    void processCleanupBatch_noTargets_noMinioCall() {
        when(gatewayLogRepository.findLogsWithBodyCollected(PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(Collections.emptyList());
        when(gatewayLogRepository.findLogsExceedingRetries(MAX_RETRIES, PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(Collections.emptyList());

        processor.processCleanupBatch();

        verify(minioObjectCleaner, never()).deleteAllByBodyUrl(anyString());
    }
}
