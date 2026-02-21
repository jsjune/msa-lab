package org.example.logbatch.integration;

import io.minio.MinioClient;
import org.example.logbatch.consumer.LogMessageDeserializer;
import org.example.logbatch.domain.BodyCollectionPolicy;
import org.example.logbatch.domain.GatewayLog;
import org.example.logbatch.domain.GatewayLogBody;
import org.example.logbatch.repository.BodyCollectionPolicyRepository;
import org.example.logbatch.repository.GatewayLogBodyRepository;
import org.example.logbatch.repository.GatewayLogRepository;
import org.example.logbatch.scheduler.BodyBatchProcessor;
import org.example.logbatch.scheduler.MinioCleanupBatchProcessor;
import org.example.logbatch.consumer.MetadataBatchProcessor;
import org.example.logbatch.storage.MinioLogFetcher;
import org.example.logbatch.storage.MinioObjectCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "batch.metadata.enabled=false")
@DisplayName("LogBatch 통합 테스트 - H2 + Mock MinIO")
class LogBatchIntegrationTest {

    @Autowired
    private GatewayLogRepository gatewayLogRepository;

    @Autowired
    private GatewayLogBodyRepository gatewayLogBodyRepository;

    @Autowired
    private BodyCollectionPolicyRepository policyRepository;

    @Autowired
    private MetadataBatchProcessor metadataProcessor;

    @Autowired
    private BodyBatchProcessor bodyProcessor;

    @Autowired
    private MinioCleanupBatchProcessor cleanupProcessor;

    @MockitoBean
    private MinioClient minioClient;

    @MockitoBean
    private MinioLogFetcher minioLogFetcher;

    @MockitoBean
    private MinioObjectCleaner minioObjectCleaner;

    private static final String JSON_TEMPLATE = """
            {"txId":"%s","hop":%d,"path":"%s","target":"http://localhost:8081%s","duration":"45ms","status":%d,"reqTime":"2026-02-17T01:23:45.678Z","resTime":"2026-02-17T01:23:45.723Z","bodyUrl":"s3://gateway-logs/2026/02/17/%s-hop%d","error":%s}
            """;

    private String buildJson(String txId, int hop, String path, int status, String error) {
        String errorVal = error != null ? "\"" + error + "\"" : "null";
        return String.format(JSON_TEMPLATE, txId, hop, path, path, status, txId, hop, errorVal);
    }

    @BeforeEach
    void setUp() {
        gatewayLogBodyRepository.deleteAll();
        gatewayLogRepository.deleteAll();
        policyRepository.deleteAll();
    }

    // ── 7.1 정상 흐름 End-to-End ──

    @Test
    @DisplayName("메타데이터만 수집 (body 수집 비활성화) → DB에 GatewayLog N건, GatewayLogBody 0건")
    void e2e_metadataOnly_noBodyCollection() {
        List<String> messages = List.of(
                buildJson("tx-1", 1, "/server-a/hello", 200, null),
                buildJson("tx-2", 1, "/server-b/data", 201, null),
                buildJson("tx-3", 1, "/server-c/info", 200, null)
        );

        boolean result = metadataProcessor.processBatch(messages);

        assertThat(result).isTrue();
        assertThat(gatewayLogRepository.count()).isEqualTo(3);
        assertThat(gatewayLogBodyRepository.count()).isZero();
    }

    @Test
    @DisplayName("메타데이터 수집 후 body 수집 → 두 파이프라인 독립 동작")
    void e2e_metadataThenBody_independentPipelines() {
        policyRepository.save(BodyCollectionPolicy.builder()
                .pathPattern("/server-a/**").enabled(true).build());

        when(minioLogFetcher.fetchAllByBodyUrl(anyString()))
                .thenReturn(new MinioLogFetcher.FetchResult(
                        "{\"name\":\"test\"}", "{\"result\":\"ok\"}",
                        "{\"Content-Type\":\"application/json\"}", "{\"Status\":\"200\"}"));

        // Phase 1: 메타데이터 수집
        List<String> messages = List.of(
                buildJson("tx-body-1", 1, "/server-a/hello", 200, null),
                buildJson("tx-body-2", 1, "/server-a/data", 200, null)
        );
        metadataProcessor.processBatch(messages);

        assertThat(gatewayLogRepository.count()).isEqualTo(2);
        assertThat(gatewayLogBodyRepository.count()).isZero();

        // Phase 2: body 수집 (DB 스캔 기반)
        bodyProcessor.processBodyBatch();

        assertThat(gatewayLogBodyRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("혼합 배치 (활성화 + 비활성화 path) → body는 활성화된 path만 수집")
    void e2e_mixedPolicies_bodyOnlyForEnabled() {
        policyRepository.save(BodyCollectionPolicy.builder()
                .pathPattern("/server-a/**").enabled(true).build());

        when(minioLogFetcher.fetchAllByBodyUrl(anyString()))
                .thenReturn(new MinioLogFetcher.FetchResult("req", "res", "rh", "rsh"));

        List<String> messages = List.of(
                buildJson("tx-mix-1", 1, "/server-a/hello", 200, null),
                buildJson("tx-mix-2", 1, "/server-b/data", 200, null),
                buildJson("tx-mix-3", 1, "/server-a/info", 200, null)
        );

        metadataProcessor.processBatch(messages);
        bodyProcessor.processBodyBatch();

        assertThat(gatewayLogRepository.count()).isEqualTo(3);
        assertThat(gatewayLogBodyRepository.count()).isEqualTo(2); // only /server-a paths
    }

    @Test
    @DisplayName("메타데이터 필드 + MinIO 컨텐츠가 DB 컬럼에 정확히 매핑")
    void e2e_fieldsMapping_correct() {
        policyRepository.save(BodyCollectionPolicy.builder()
                .pathPattern("/server-a/**").enabled(true).build());

        when(minioLogFetcher.fetchAllByBodyUrl(anyString()))
                .thenReturn(new MinioLogFetcher.FetchResult(
                        "request-body-content", "response-body-content",
                        "req-header-json", "res-header-json"));

        metadataProcessor.processBatch(List.of(
                buildJson("tx-map", 1, "/server-a/hello", 200, null)));
        bodyProcessor.processBodyBatch();

        List<GatewayLog> logs = gatewayLogRepository.findByTxIdWithBody("tx-map");
        assertThat(logs).hasSize(1);

        GatewayLog log = logs.get(0);
        assertThat(log.getTxId()).isEqualTo("tx-map");
        assertThat(log.getHop()).isEqualTo(1);
        assertThat(log.getPath()).isEqualTo("/server-a/hello");
        assertThat(log.getStatus()).isEqualTo(200);
        assertThat(log.getDurationMs()).isEqualTo(45L);
        assertThat(log.getPartitionDay()).isEqualTo(17);

        GatewayLogBody body = log.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getRequestBody()).isEqualTo("request-body-content");
        assertThat(body.getResponseBody()).isEqualTo("response-body-content");
        assertThat(body.getRequestHeaders()).isEqualTo("req-header-json");
        assertThat(body.getResponseHeaders()).isEqualTo("res-header-json");
    }

    @Test
    @DisplayName("Kafka 리밸런싱 재처리 시 중복 건 skip, 신규 건만 저장")
    void e2e_reprocessWithDuplicates_newRecordSaved() {
        // Phase 1: 첫 번째 처리 — tx-dup-1 저장
        metadataProcessor.processBatch(List.of(
                buildJson("tx-dup-1", 1, "/server-a/hello", 200, null)));
        assertThat(gatewayLogRepository.count()).isEqualTo(1);

        // Phase 2: 재처리 (Kafka 리밸런싱 시나리오) — 기존 tx-dup-1 + 신규 tx-new 포함
        boolean result = metadataProcessor.processBatch(List.of(
                buildJson("tx-dup-1", 1, "/server-a/hello", 200, null), // 중복
                buildJson("tx-new",   1, "/server-b/data",  200, null)  // 신규
        ));

        assertThat(result).isTrue();
        assertThat(gatewayLogRepository.count()).isEqualTo(2); // 중복 1건 skip, 신규 1건 추가
    }

    // ── 7.2 에러 시나리오 ──

    @Test
    @DisplayName("MinIO 전체 장애 → GatewayLog만 저장, body 수집 시 GatewayLogBody 누락")
    void e2e_minioDown_metadataOnlyStored() {
        policyRepository.save(BodyCollectionPolicy.builder()
                .pathPattern("/server-a/**").enabled(true).build());

        when(minioLogFetcher.fetchAllByBodyUrl(anyString()))
                .thenReturn(new MinioLogFetcher.FetchResult(null, null, null, null));

        metadataProcessor.processBatch(List.of(
                buildJson("tx-minio-fail", 1, "/server-a/hello", 200, null)));
        bodyProcessor.processBodyBatch();

        assertThat(gatewayLogRepository.count()).isEqualTo(1);
        assertThat(gatewayLogBodyRepository.count()).isZero();
    }

    @Test
    @DisplayName("poison pill 메시지 섞인 배치 → 정상 건만 저장")
    void e2e_poisonPill_validOnlySaved() {
        List<String> messages = List.of(
                buildJson("tx-ok-1", 1, "/server-a/hello", 200, null),
                "{this is not valid json!!!}",
                buildJson("tx-ok-2", 1, "/server-b/data", 200, null)
        );

        boolean result = metadataProcessor.processBatch(messages);

        assertThat(result).isTrue();
        assertThat(gatewayLogRepository.count()).isEqualTo(2);
    }

    // ── 7.3 파이프라인 독립성 ──

    @Test
    @DisplayName("bodyUrl=null인 로그는 body 수집 배치에서 제외되어 MinIO 조회를 하지 않는다")
    void e2e_bodyUrlNull_skipsMinioCall() {
        // given — gateway가 모든 업로드 실패 시 bodyUrl=null로 Kafka 전송 → DB에 null로 저장
        gatewayLogRepository.save(GatewayLog.builder()
                .txId("null-url-tx")
                .hop(1)
                .path("/server-a/hello")
                .status(200)
                .partitionDay(17)
                .build()); // bodyUrl 미설정 = null

        // when
        bodyProcessor.processBodyBatch();

        // then — bodyUrl IS NOT NULL 조건으로 쿼리에서 제외 → MinIO 미호출
        verify(minioLogFetcher, never()).fetchAllByBodyUrl(anyString());
        assertThat(gatewayLogBodyRepository.count()).isZero();
    }

    @Test
    @DisplayName("body 수집을 메타데이터 수집 없이 단독 실행 가능")
    void e2e_bodyBatchAlone_worksIndependently() {
        policyRepository.save(BodyCollectionPolicy.builder()
                .pathPattern("/server-a/**").enabled(true).build());

        // DB에 직접 로그 삽입 (메타데이터 프로세서 거치지 않음)
        GatewayLog log = gatewayLogRepository.save(GatewayLog.builder()
                .txId("direct-insert")
                .hop(1)
                .path("/server-a/hello")
                .status(200)
                .bodyUrl("s3://gateway-logs/2026/02/17/direct-insert-hop1")
                .partitionDay(17)
                .build());

        when(minioLogFetcher.fetchAllByBodyUrl(log.getBodyUrl()))
                .thenReturn(new MinioLogFetcher.FetchResult("req", "res", "rh", "rsh"));

        bodyProcessor.processBodyBatch();

        assertThat(gatewayLogBodyRepository.count()).isEqualTo(1);
    }

    // ── 7.4 MinIO 정리 배치 ──

    @Test
    @DisplayName("cleanup 실행 후 body 수집 완료 레코드의 bodyUrl=null 확인")
    void e2e_cleanupBatch_collectedLogs_bodyUrlCleared() {
        policyRepository.save(BodyCollectionPolicy.builder()
                .pathPattern("/server-a/**").enabled(true).build());

        when(minioLogFetcher.fetchAllByBodyUrl(anyString()))
                .thenReturn(new MinioLogFetcher.FetchResult("req", "res", "rh", "rsh"));
        when(minioObjectCleaner.deleteAllByBodyUrl(anyString())).thenReturn(true);

        // Phase 1: 메타데이터 + body 수집
        metadataProcessor.processBatch(List.of(
                buildJson("tx-cleanup-1", 1, "/server-a/hello", 200, null),
                buildJson("tx-cleanup-2", 1, "/server-a/data",  200, null)));
        bodyProcessor.processBodyBatch();
        assertThat(gatewayLogBodyRepository.count()).isEqualTo(2);

        // Phase 2: cleanup 실행
        cleanupProcessor.processCleanupBatch();

        // body 수집 완료 레코드의 bodyUrl이 null로 업데이트 확인
        long bodyUrlNullCount = gatewayLogRepository.findAll().stream()
                .filter(l -> l.getBodyUrl() == null).count();
        assertThat(bodyUrlNullCount).isEqualTo(2);
    }
}
