package org.example.admin.integration;

import org.example.admin.domain.BodyCollectionPolicy;
import org.example.admin.domain.GatewayLog;
import org.example.admin.domain.GatewayLogBody;
import org.example.admin.repository.BodyCollectionPolicyRepository;
import org.example.admin.repository.GatewayLogBodyRepository;
import org.example.admin.repository.GatewayLogReadRepository;
import org.example.admin.service.BodyCollectionPolicyService;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GatewayLogReadRepository repository;

    @Autowired
    private GatewayLogBodyRepository bodyRepository;

    @Autowired
    private BodyCollectionPolicyRepository policyRepository;

    @Autowired
    private BodyCollectionPolicyService policyService;

    private static final Instant BASE_TIME = Instant.parse("2026-02-20T10:00:00Z");

    @AfterEach
    void cleanup() {
        policyRepository.deleteAll();
        bodyRepository.deleteAll();
        repository.deleteAll();
    }

    private GatewayLog saveHop(String txId, int hop, String path, int status, Long durationMs, Instant reqTime) {
        return repository.save(GatewayLog.builder()
                .txId(txId)
                .hop(hop)
                .path(path)
                .status(status)
                .durationMs(durationMs)
                .reqTime(reqTime)
                .resTime(reqTime.plusMillis(durationMs))
                .partitionDay(reqTime.atZone(java.time.ZoneOffset.UTC).getDayOfMonth())
                .build());
    }

    private void saveLog(String txId, String path, int status, Long durationMs) {
        repository.save(GatewayLog.builder()
                .txId(txId)
                .hop(1)
                .path(path)
                .status(status)
                .durationMs(durationMs)
                .reqTime(BASE_TIME)
                .resTime(BASE_TIME.plusMillis(durationMs))
                .partitionDay(20)
                .build());
    }

    private void saveLogAt(String txId, String path, int status, Long durationMs, Instant reqTime) {
        repository.save(GatewayLog.builder()
                .txId(txId)
                .hop(1)
                .path(path)
                .status(status)
                .durationMs(durationMs)
                .reqTime(reqTime)
                .resTime(reqTime.plusMillis(durationMs))
                .partitionDay(reqTime.atZone(java.time.ZoneOffset.UTC).getDayOfMonth())
                .build());
    }

    // === Phase 7.1 통계 E2E ===

    @Test
    @DisplayName("기간 필터 — 기간 외 데이터는 집계에서 제외되는지 확인")
    void apiStats_e2e_periodFilter() throws Exception {
        Instant inPeriod = Instant.parse("2026-02-20T10:00:00Z");
        Instant outOfPeriod = Instant.parse("2026-02-20T08:00:00Z"); // from=09:00 이전

        // 기간 내 3건
        saveLogAt("tx-f1", "/server-a/chain", 200, 10L, inPeriod);
        saveLogAt("tx-f2", "/server-a/chain", 200, 20L, inPeriod);
        saveLogAt("tx-f3", "/server-a/chain", 200, 30L, inPeriod);
        // 기간 외 2건 (집계 대상 아님)
        saveLogAt("tx-f4", "/server-a/chain", 200, 100L, outOfPeriod);
        saveLogAt("tx-f5", "/server-a/chain", 200, 200L, outOfPeriod);

        mockMvc.perform(get("/api/stats")
                        .param("from", "2026-02-20T09:00:00Z")
                        .param("to", "2026-02-20T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value("/server-a/chain"))
                .andExpect(jsonPath("$[0].count").value(3)); // 기간 내 3건만 집계
    }

    @Test
    @DisplayName("대량 데이터 (1001건) — p50 백분위 계산 정확성 검증")
    void apiStats_e2e_largeData_percentileAccuracy() throws Exception {
        // durationMs = 1, 2, ..., 1001 → 정렬 시 p50 index = 1000*0.5 = 500 → list[500] = 501
        java.util.List<GatewayLog> logs = new java.util.ArrayList<>();
        for (int i = 1; i <= 1001; i++) {
            logs.add(GatewayLog.builder()
                    .txId("tx-large-" + i)
                    .hop(1)
                    .path("/server-a/chain")
                    .status(200)
                    .durationMs((long) i)
                    .reqTime(BASE_TIME)
                    .resTime(BASE_TIME.plusMillis(i))
                    .partitionDay(20)
                    .build());
        }
        repository.saveAll(logs);

        mockMvc.perform(get("/api/stats")
                        .param("from", "2026-02-20T09:00:00Z")
                        .param("to", "2026-02-20T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].count").value(1001))
                .andExpect(jsonPath("$[0].p50").value(501));
    }

    // === Phase 8: Edge Cases & 운영 ===

    @Test
    @DisplayName("페이징 처리 — size=2 요청 시 2건만 반환, totalElements=5, totalPages=3")
    void traces_e2e_pagination_limitPerPage() throws Exception {
        for (int i = 1; i <= 5; i++) {
            saveHop("tx-page-" + i, 1, "/server-a/chain", 200, 10L, BASE_TIME.minusSeconds(i));
        }

        mockMvc.perform(get("/api/traces")
                        .param("from", "2026-02-20T09:00:00Z")
                        .param("to", "2026-02-20T11:00:00Z")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    @DisplayName("여러 스레드 동시 정책 토글 — 예외 없이 완료되고 데이터 무결성 유지")
    void policy_concurrentToggle_noExceptionAndPolicyIntact() throws InterruptedException {
        policyRepository.save(BodyCollectionPolicy.builder()
                .pathPattern("/concurrent-test/**")
                .enabled(false)
                .build());
        Long id = policyRepository.findAll().get(0).getId();

        int threadCount = 4;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    policyService.toggle(id);
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // 예외 없이 완료됨 (데이터 무결성 유지)
        assertThat(exceptions).isEmpty();
        // 정책이 삭제되지 않고 존재함
        assertThat(policyRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("통계 조회 일관성 — 새 데이터 커밋 후 재조회 시 업데이트된 값 반환")
    void apiStats_e2e_readCommittedConsistency() throws Exception {
        // 1차 배치: 3건 커밋
        saveLog("tx-c1", "/server-a/chain", 200, 10L);
        saveLog("tx-c2", "/server-a/chain", 200, 20L);
        saveLog("tx-c3", "/server-a/chain", 200, 30L);

        mockMvc.perform(get("/api/stats")
                        .param("from", "2026-02-20T09:00:00Z")
                        .param("to", "2026-02-20T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].count").value(3));

        // 2차 배치: 2건 추가 커밋 (batch-server 역할)
        saveLog("tx-c4", "/server-a/chain", 200, 40L);
        saveLog("tx-c5", "/server-a/chain", 200, 50L);

        // 재조회 — 커밋된 5건 반영
        mockMvc.perform(get("/api/stats")
                        .param("from", "2026-02-20T09:00:00Z")
                        .param("to", "2026-02-20T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].count").value(5));
    }

    // === Phase 7.3 정책 관리 E2E ===

    @Test
    @DisplayName("정책 생성 → 조회 → 토글 → 삭제 전체 사이클")
    void policy_e2e_crudCycle() throws Exception {
        // 1. 정책 생성
        mockMvc.perform(post("/api/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathPattern\":\"/server-test/**\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pathPattern").value("/server-test/**"))
                .andExpect(jsonPath("$.enabled").value(false));

        Long id = policyRepository.findAll().get(0).getId();

        // 2. 목록 조회 — 1건 존재
        mockMvc.perform(get("/api/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].pathPattern").value("/server-test/**"));

        // 3. 활성화 토글 — enabled: false → true
        mockMvc.perform(patch("/api/policies/" + id + "/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // 4. 삭제
        mockMvc.perform(delete("/api/policies/" + id))
                .andExpect(status().isNoContent());

        // 5. 삭제 후 빈 목록
        mockMvc.perform(get("/api/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("repository 직접 저장과 API 조회가 동일 테이블(body_collection_policy) 참조")
    void policy_e2e_sameTableSchema() throws Exception {
        // batch-server 역할: repository로 직접 저장
        policyRepository.save(BodyCollectionPolicy.builder()
                .pathPattern("/server-schema-test/**")
                .enabled(false)
                .build());

        // admin-server API로 조회 — 같은 테이블이면 조회돼야 함
        mockMvc.perform(get("/api/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].pathPattern").value("/server-schema-test/**"));
    }

    // === Phase 7.2 분산추적 E2E ===

    @Test
    @DisplayName("3-hop 체인 데이터 삽입 → /api/traces/{txId} → 전체 hop 체인 반환")
    void traces_e2e_threeHopChain() throws Exception {
        // given — 동일 txId, hop=1,2,3
        Instant t1 = BASE_TIME;
        Instant t2 = BASE_TIME.plusMillis(10);
        Instant t3 = BASE_TIME.plusMillis(20);
        saveHop("tx-chain", 1, "/server-a/chain", 200, 100L, t1);
        saveHop("tx-chain", 2, "/server-b/chain", 200, 80L, t2);
        saveHop("tx-chain", 3, "/server-c/data",  200, 50L, t3);

        mockMvc.perform(get("/api/traces/tx-chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txId").value("tx-chain"))
                .andExpect(jsonPath("$.hopCount").value(3))
                .andExpect(jsonPath("$.hops[0].hop").value(1))
                .andExpect(jsonPath("$.hops[1].hop").value(2))
                .andExpect(jsonPath("$.hops[2].hop").value(3))
                .andExpect(jsonPath("$.hops[0].path").value("/server-a/chain"))
                .andExpect(jsonPath("$.hops[2].path").value("/server-c/data"));
    }

    @Test
    @DisplayName("body 포함 조회 — includeDetail=true 시 requestBody/responseBody 포함")
    void traces_e2e_includeDetail_bodyReturned() throws Exception {
        // given
        GatewayLog log = saveHop("tx-body", 1, "/server-a/chain", 200, 50L, BASE_TIME);
        bodyRepository.save(GatewayLogBody.builder()
                .gatewayLog(log)
                .requestBody("{\"key\":\"value\"}")
                .responseBody("{\"result\":\"ok\"}")
                .requestHeaders("{}")
                .responseHeaders("{}")
                .build());

        mockMvc.perform(get("/api/traces/tx-body").param("includeDetail", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hopCount").value(1))
                .andExpect(jsonPath("$.hops[0].requestBody").value("{\"key\":\"value\"}"))
                .andExpect(jsonPath("$.hops[0].responseBody").value("{\"result\":\"ok\"}"));
    }

    @Test
    @DisplayName("검색 필터 — path 필터로 해당 path 포함 txId만 반환")
    void traces_e2e_searchFilter_byPath() throws Exception {
        // given — 두 txId, 서로 다른 path
        saveHop("tx-path-a", 1, "/server-a/chain", 200, 10L, BASE_TIME);
        saveHop("tx-path-b", 1, "/server-b/chain", 200, 20L, BASE_TIME);

        // /server-a/chain 경로로 필터링
        mockMvc.perform(get("/api/traces")
                        .param("from", "2026-02-20T09:00:00Z")
                        .param("to", "2026-02-20T11:00:00Z")
                        .param("path", "/server-a/chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].txId").value("tx-path-a"));
    }

    @Test
    @DisplayName("테스트 데이터 삽입 → /api/stats 호출 → 정확한 백분위/에러율 검증")
    void apiStats_e2e_percentileAndErrorRate() throws Exception {
        // duration: 10, 20, 30, 40, 50 / 2개 에러(status 500)
        saveLog("tx-s1", "/server-a/chain", 200, 10L);
        saveLog("tx-s2", "/server-a/chain", 200, 20L);
        saveLog("tx-s3", "/server-a/chain", 200, 30L);
        saveLog("tx-s4", "/server-a/chain", 500, 40L);
        saveLog("tx-s5", "/server-a/chain", 500, 50L);

        // p50 of [10,20,30,40,50] = 30 (index=2.0, exact)
        // errorCount = 2, errorRate = 40.00%
        mockMvc.perform(get("/api/stats")
                        .param("from", "2026-02-20T09:00:00Z")
                        .param("to", "2026-02-20T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value("/server-a/chain"))
                .andExpect(jsonPath("$[0].count").value(5))
                .andExpect(jsonPath("$[0].errorCount").value(2))
                .andExpect(jsonPath("$[0].p50").value(30));
    }
}
