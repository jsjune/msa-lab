package org.example.logbatch.scheduler;

import org.example.logbatch.config.BatchProperties;
import org.example.logbatch.domain.GatewayLog;
import org.example.logbatch.domain.GatewayLogBody;
import org.example.logbatch.repository.GatewayLogBodyRepository;
import org.example.logbatch.repository.GatewayLogRepository;
import org.example.logbatch.service.BodyCollectionService;
import org.example.logbatch.storage.MinioLogFetcher;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BodyBatchProcessor - DB 스캔 기반 바디 수집")
class BodyBatchProcessorTest {

    @Mock
    private GatewayLogRepository gatewayLogRepository;

    @Mock
    private GatewayLogBodyRepository gatewayLogBodyRepository;

    @Mock
    private BodyCollectionService bodyCollectionService;

    @Mock
    private MinioLogFetcher minioLogFetcher;

    private BodyBatchProcessor processor;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 3;

    @BeforeEach
    void setUp() {
        BatchProperties batchProperties = new BatchProperties(
                new BatchProperties.MetadataProperties(true, "gateway-meta-logs"),
                new BatchProperties.BodyProperties(true, 30000, BATCH_SIZE, MAX_RETRIES));
        processor = new BodyBatchProcessor(
                gatewayLogRepository, gatewayLogBodyRepository,
                bodyCollectionService, minioLogFetcher, batchProperties);
    }

    private GatewayLog createLog(String txId, int hop, String path) {
        return GatewayLog.builder()
                .id((long) hop)
                .txId(txId)
                .hop(hop)
                .path(path)
                .bodyUrl("s3://gateway-logs/2026/02/17/" + txId + "-hop" + hop)
                .partitionDay(17)
                .build();
    }

    // ── DB 스캔 기반 바디 수집 ──

    @Test
    @DisplayName("바디 미수집 로그 조회 → MinIO fetch → GatewayLogBody 저장")
    void processBodyBatch_collectsBodyFromDb() {
        GatewayLog log = createLog("tx-1", 1, "/server-a/hello");
        when(gatewayLogRepository.findLogsNeedingBodyCollection(MAX_RETRIES, PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(List.of(log));
        when(bodyCollectionService.shouldCollectBody("/server-a/hello")).thenReturn(true);
        when(minioLogFetcher.fetchAllByBodyUrl(log.getBodyUrl()))
                .thenReturn(new MinioLogFetcher.FetchResult("req", "res", "rh", "rsh"));

        processor.processBodyBatch();

        verify(gatewayLogBodyRepository).save(any(GatewayLogBody.class));
    }

    @Test
    @DisplayName("바디 미수집 로그 없음 → MinIO 호출 안 함")
    void processBodyBatch_noLogsNeedingBody_noMinioCall() {
        when(gatewayLogRepository.findLogsNeedingBodyCollection(MAX_RETRIES, PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(Collections.emptyList());

        processor.processBodyBatch();

        verify(minioLogFetcher, never()).fetchAllByBodyUrl(anyString());
        verify(gatewayLogBodyRepository, never()).save(any(GatewayLogBody.class));
    }

    @Test
    @DisplayName("정책에 의해 비활성화된 path → MinIO 호출 안 함, body 저장 안 함")
    void processBodyBatch_policyDisabled_skipped() {
        GatewayLog log = createLog("tx-1", 1, "/server-b/data");
        when(gatewayLogRepository.findLogsNeedingBodyCollection(MAX_RETRIES, PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(List.of(log));
        when(bodyCollectionService.shouldCollectBody("/server-b/data")).thenReturn(false);

        processor.processBodyBatch();

        verify(minioLogFetcher, never()).fetchAllByBodyUrl(anyString());
        verify(gatewayLogBodyRepository, never()).save(any(GatewayLogBody.class));
    }

    @Test
    @DisplayName("MinIO 조회 실패 (모든 값 null) → body 저장 안 함, retryCount 증가")
    void processBodyBatch_minioFailure_noBodySaved() {
        GatewayLog log = createLog("tx-1", 1, "/server-a/hello");
        when(gatewayLogRepository.findLogsNeedingBodyCollection(MAX_RETRIES, PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(List.of(log));
        when(bodyCollectionService.shouldCollectBody("/server-a/hello")).thenReturn(true);
        when(minioLogFetcher.fetchAllByBodyUrl(anyString()))
                .thenReturn(new MinioLogFetcher.FetchResult(null, null, null, null));

        processor.processBodyBatch();

        verify(gatewayLogBodyRepository, never()).save(any(GatewayLogBody.class));
        verify(gatewayLogRepository).save(log);
        assertThat(log.getBodyRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("활성화/비활성화 path 혼재 → 각각 올바르게 처리")
    void processBodyBatch_mixedPolicies_handledCorrectly() {
        GatewayLog enabled = createLog("tx-1", 1, "/server-a/hello");
        GatewayLog disabled = createLog("tx-2", 1, "/server-b/data");
        when(gatewayLogRepository.findLogsNeedingBodyCollection(MAX_RETRIES, PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(List.of(enabled, disabled));
        when(bodyCollectionService.shouldCollectBody("/server-a/hello")).thenReturn(true);
        when(bodyCollectionService.shouldCollectBody("/server-b/data")).thenReturn(false);
        when(minioLogFetcher.fetchAllByBodyUrl(enabled.getBodyUrl()))
                .thenReturn(new MinioLogFetcher.FetchResult("req", "res", "rh", "rsh"));

        processor.processBodyBatch();

        verify(minioLogFetcher).fetchAllByBodyUrl(enabled.getBodyUrl());
        verify(minioLogFetcher, never()).fetchAllByBodyUrl(disabled.getBodyUrl());
        verify(gatewayLogBodyRepository, times(1)).save(any(GatewayLogBody.class));
    }

    // ── 중복 실행 방지 ──

    @Test
    @DisplayName("이전 배치가 완료되지 않으면 다음 실행 건너뜀")
    void concurrentExecution_skipped() {
        GatewayLog log = createLog("tx-1", 1, "/server-a/hello");
        when(gatewayLogRepository.findLogsNeedingBodyCollection(eq(MAX_RETRIES), any()))
                .thenReturn(List.of(log));
        when(bodyCollectionService.shouldCollectBody(anyString())).thenReturn(true);
        when(minioLogFetcher.fetchAllByBodyUrl(anyString())).thenAnswer(invocation -> {
            // 진행 중일 때 두 번째 호출 시도
            assertThat(processor.isRunning()).isTrue();
            return new MinioLogFetcher.FetchResult("req", "res", "rh", "rsh");
        });

        processor.processBodyBatch();

        assertThat(processor.isRunning()).isFalse();
    }

    // ── 정책 캐시 관리 ──

    @Test
    @DisplayName("배치 시작 시 정책 캐시 갱신, 종료 시 캐시 클리어")
    void processBodyBatch_policyCacheLifecycle() {
        when(gatewayLogRepository.findLogsNeedingBodyCollection(MAX_RETRIES, PageRequest.of(0, BATCH_SIZE)))
                .thenReturn(Collections.emptyList());

        processor.processBodyBatch();

        verify(bodyCollectionService).refreshPolicyCache();
        verify(bodyCollectionService).clearPolicyCache();
    }
}
