package org.example.admin.repository;

import org.example.admin.domain.GatewayLog;
import org.example.admin.domain.GatewayLogBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class GatewayLogReadRepositoryTest {

    @Autowired
    private GatewayLogReadRepository repository;

    @Autowired
    private TestEntityManager em;

    private static final Instant BASE_TIME = Instant.parse("2026-02-20T10:00:00Z");

    @BeforeEach
    void setUp() {
        // 3개 path, 다양한 status
        persistLog("tx-1", 1, "/server-a/chain", 200, 100L, BASE_TIME);
        persistLog("tx-1", 2, "/server-b/chain", 200, 50L, BASE_TIME.plusMillis(200));
        persistLog("tx-1", 3, "/server-c/chain", 200, 30L, BASE_TIME.plusMillis(400));
        persistLog("tx-2", 1, "/server-a/chain", 500, 200L, BASE_TIME.plusSeconds(60));
        persistLog("tx-3", 1, "/server-a/chain", 200, 150L, BASE_TIME.plusSeconds(120));
        persistLog("tx-4", 1, "/server-b/data", 404, 80L, BASE_TIME.plusSeconds(180));
        em.flush();
        em.clear();
    }

    private void persistLog(String txId, int hop, String path, int status, Long durationMs, Instant reqTime) {
        em.persist(GatewayLog.builder()
                .txId(txId)
                .hop(hop)
                .path(path)
                .target("http://localhost/" + path)
                .durationMs(durationMs)
                .status(status)
                .reqTime(reqTime)
                .resTime(reqTime.plusMillis(durationMs))
                .bodyUrl("s3://gateway-logs/" + txId + "-hop" + hop)
                .partitionDay(20)
                .build());
    }

    // === 3.1 통계 쿼리 ===

    @Test
    @DisplayName("path별 group by → 요청 수(count), 에러 수(status >= 400) 집계")
    void findPathStats_groupByPath() {
        Instant from = BASE_TIME.minusSeconds(10);
        Instant to = BASE_TIME.plusSeconds(300);

        List<PathStatsProjection> stats = repository.findPathStats(from, to);

        assertThat(stats).hasSizeGreaterThanOrEqualTo(3);

        PathStatsProjection serverA = stats.stream()
                .filter(s -> "/server-a/chain".equals(s.getPath())).findFirst().orElseThrow();
        assertThat(serverA.getCount()).isEqualTo(3);
        assertThat(serverA.getErrorCount()).isEqualTo(1); // tx-2 status=500
    }

    @Test
    @DisplayName("기간 필터 적용 — 범위 밖 데이터 제외")
    void findPathStats_periodFilter() {
        Instant from = BASE_TIME.plusSeconds(50);
        Instant to = BASE_TIME.plusSeconds(130);

        List<PathStatsProjection> stats = repository.findPathStats(from, to);

        // tx-2 (60s), tx-3 (120s) — /server-a/chain만 해당
        assertThat(stats).hasSize(1);
        assertThat(stats.getFirst().getCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("데이터 없는 기간 → 빈 결과")
    void findPathStats_emptyPeriod() {
        Instant from = BASE_TIME.plusSeconds(1000);
        Instant to = BASE_TIME.plusSeconds(2000);

        List<PathStatsProjection> stats = repository.findPathStats(from, to);

        assertThat(stats).isEmpty();
    }

    // === 3.2 응답시간 리스트 ===

    @Test
    @DisplayName("path별 duration_ms 리스트 조회 — 정렬된 결과")
    void findDurationsByPath_sorted() {
        Instant from = BASE_TIME.minusSeconds(10);
        Instant to = BASE_TIME.plusSeconds(300);

        List<Long> durations = repository.findDurationsByPath("/server-a/chain", from, to);

        assertThat(durations).containsExactly(100L, 150L, 200L);
    }

    @Test
    @DisplayName("duration_ms 기간 필터 적용")
    void findDurationsByPath_periodFilter() {
        Instant from = BASE_TIME.plusSeconds(50);
        Instant to = BASE_TIME.plusSeconds(130);

        List<Long> durations = repository.findDurationsByPath("/server-a/chain", from, to);

        assertThat(durations).containsExactly(150L, 200L);
    }

    @Test
    @DisplayName("null duration 제외")
    void findDurationsByPath_nullExcluded() {
        em.persist(GatewayLog.builder()
                .txId("tx-null").hop(1).path("/server-a/chain")
                .status(200).durationMs(null)
                .reqTime(BASE_TIME.plusSeconds(10))
                .partitionDay(20).build());
        em.flush();
        em.clear();

        List<Long> durations = repository.findDurationsByPath("/server-a/chain",
                BASE_TIME.minusSeconds(10), BASE_TIME.plusSeconds(300));

        assertThat(durations).doesNotContainNull();
    }

    // === 3.3 분산추적 쿼리 ===

    @Test
    @DisplayName("txId로 조회 → 해당 txId의 모든 hop 반환 (hop 순 정렬)")
    void findByTxId_hopsOrdered() {
        List<GatewayLog> hops = repository.findByTxIdOrderByHop("tx-1");

        assertThat(hops).hasSize(3);
        assertThat(hops).extracting(GatewayLog::getHop).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("txId로 조회 시 body JOIN fetch — body 포함")
    void findByTxIdWithBody() {
        // body 데이터 삽입
        GatewayLog log = repository.findByTxIdOrderByHop("tx-1").getFirst();
        em.persist(GatewayLogBody.builder()
                .gatewayLog(log)
                .requestBody("{\"key\":\"value\"}")
                .responseBody("{\"result\":\"ok\"}")
                .build());
        em.flush();
        em.clear();

        List<GatewayLog> hops = repository.findByTxIdWithBody("tx-1");

        assertThat(hops).hasSize(3);
        assertThat(hops.getFirst().getBody()).isNotNull();
        assertThat(hops.getFirst().getBody().getRequestBody()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    @DisplayName("존재하지 않는 txId → 빈 결과")
    void findByTxId_notFound() {
        List<GatewayLog> hops = repository.findByTxIdOrderByHop("non-existent");

        assertThat(hops).isEmpty();
    }

    // === 3.4 분산추적 검색 ===

    @Test
    @DisplayName("기간 내 txId + reqTime 목록 조회 — 페이징")
    void findDistinctTxIds_paged() {
        Instant from = BASE_TIME.minusSeconds(10);
        Instant to = BASE_TIME.plusSeconds(300);

        Page<TraceSummaryProjection> page = repository.findDistinctTxIds(from, to, PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().getFirst().getTxId()).isNotNull();
        assertThat(page.getContent().getFirst().getReqTime()).isNotNull();
    }

    @Test
    @DisplayName("path 필터로 txId 목록 조회")
    void findDistinctTxIdsByPath() {
        Instant from = BASE_TIME.minusSeconds(10);
        Instant to = BASE_TIME.plusSeconds(300);

        Page<TraceSummaryProjection> page = repository.findDistinctTxIdsByPath(from, to, "/server-b/data", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(TraceSummaryProjection::getTxId).containsExactly("tx-4");
    }

    // === 3.6 서비스 그래프 쿼리 ===

    @Test
    @DisplayName("서비스 그래프 쿼리 — null durationMs 행은 결과에서 제외")
    void findHopRawData_nullDurationExcluded() {
        em.persist(GatewayLog.builder()
                .txId("tx-null-dur").hop(1).path("/server-a/chain")
                .status(200).durationMs(null)
                .reqTime(BASE_TIME.plusSeconds(10))
                .partitionDay(20).build());
        em.flush();
        em.clear();

        Instant from = BASE_TIME.minusSeconds(10);
        Instant to = BASE_TIME.plusSeconds(300);

        List<HopRawProjection> rows = repository.findHopRawData(from, to);

        assertThat(rows).extracting(HopRawProjection::getTxId)
                .doesNotContain("tx-null-dur");
    }

    @Test
    @DisplayName("서비스 그래프 쿼리 기간 필터 적용 — 범위 밖 hop 제외")
    void findHopRawData_periodFilter() {
        // BASE_TIME+61s ~ BASE_TIME+130s → tx-2(60s 제외), tx-3(120s 포함)만
        Instant from = BASE_TIME.plusSeconds(61);
        Instant to = BASE_TIME.plusSeconds(130);

        List<HopRawProjection> rows = repository.findHopRawData(from, to);

        assertThat(rows).extracting(HopRawProjection::getTxId)
                .containsOnly("tx-3");
    }

    @Test
    @DisplayName("기간 내 hop 원시 데이터 목록 조회 — txId, hop, path, status, durationMs 반환")
    void findHopRawData_returnsExpectedFields() {
        Instant from = BASE_TIME.minusSeconds(10);
        Instant to = BASE_TIME.plusSeconds(300);

        List<HopRawProjection> rows = repository.findHopRawData(from, to);

        assertThat(rows).isNotEmpty();
        HopRawProjection first = rows.stream()
                .filter(r -> "tx-1".equals(r.getTxId()) && r.getHop() == 1)
                .findFirst().orElseThrow();
        assertThat(first.getTxId()).isEqualTo("tx-1");
        assertThat(first.getHop()).isEqualTo(1);
        assertThat(first.getPath()).isEqualTo("/server-a/chain");
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(first.getDurationMs()).isEqualTo(100L);
    }

    @Test
    @DisplayName("status 필터 (에러만) → 관련 txId 목록")
    void findDistinctTxIdsByError() {
        Instant from = BASE_TIME.minusSeconds(10);
        Instant to = BASE_TIME.plusSeconds(300);

        Page<TraceSummaryProjection> page = repository.findDistinctTxIdsByError(from, to, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(TraceSummaryProjection::getTxId)
                .containsExactlyInAnyOrder("tx-2", "tx-4");
    }
}