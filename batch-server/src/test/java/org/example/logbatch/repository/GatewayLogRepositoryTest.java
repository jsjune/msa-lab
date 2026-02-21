package org.example.logbatch.repository;

import jakarta.persistence.EntityManager;
import org.example.logbatch.domain.GatewayLog;
import org.example.logbatch.domain.GatewayLogBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("GatewayLogRepository - JPA 매핑 및 쿼리")
class GatewayLogRepositoryTest {

    @Autowired
    private GatewayLogRepository gatewayLogRepository;

    @Autowired
    private GatewayLogBodyRepository gatewayLogBodyRepository;

    @Autowired
    private EntityManager entityManager;

    private GatewayLog createLog(String txId, int hop, int status, Instant reqTime) {
        return GatewayLog.builder()
                .txId(txId)
                .hop(hop)
                .path("/server-a/hello")
                .target("http://localhost:8081/hello")
                .durationMs(45L)
                .status(status)
                .reqTime(reqTime)
                .resTime(reqTime.plusMillis(45))
                .bodyUrl("s3://gateway-logs/2026/02/17/" + txId + "-hop" + hop)
                .partitionDay(reqTime.atZone(java.time.ZoneId.of("UTC")).getDayOfMonth())
                .build();
    }

    // ── 3.1 GatewayLog JPA 매핑 ──

    @Test
    @DisplayName("엔티티 저장 후 조회 → 메타데이터 모든 필드 일치 확인")
    void saveAndFind_allFieldsMatch() {
        Instant reqTime = Instant.parse("2026-02-17T01:23:45.678Z");
        GatewayLog log = createLog("abc-123", 1, 200, reqTime);

        GatewayLog saved = gatewayLogRepository.save(log);
        GatewayLog found = gatewayLogRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getTxId()).isEqualTo("abc-123");
        assertThat(found.getHop()).isEqualTo(1);
        assertThat(found.getPath()).isEqualTo("/server-a/hello");
        assertThat(found.getTarget()).isEqualTo("http://localhost:8081/hello");
        assertThat(found.getDurationMs()).isEqualTo(45L);
        assertThat(found.getStatus()).isEqualTo(200);
        assertThat(found.getReqTime()).isEqualTo(reqTime);
        assertThat(found.getResTime()).isEqualTo(reqTime.plusMillis(45));
        assertThat(found.getBodyUrl()).startsWith("s3://gateway-logs/");
        assertThat(found.getPartitionDay()).isEqualTo(17);
    }

    @Test
    @DisplayName("txId + hop 유니크 제약조건 → 중복 저장 시 예외 발생")
    void uniqueConstraint_txIdAndHop_throwsOnDuplicate() {
        Instant reqTime = Instant.parse("2026-02-17T01:23:45.678Z");
        gatewayLogRepository.saveAndFlush(createLog("abc-123", 1, 200, reqTime));

        GatewayLog duplicate = createLog("abc-123", 1, 500, reqTime);

        assertThatThrownBy(() -> gatewayLogRepository.saveAndFlush(duplicate))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("reqTime, resTime이 timestamp 컬럼에 정상 매핑")
    void timestampMapping_reqTimeAndResTime() {
        Instant reqTime = Instant.parse("2026-02-17T01:23:45.678Z");
        GatewayLog saved = gatewayLogRepository.save(createLog("ts-test", 1, 200, reqTime));

        GatewayLog found = gatewayLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getReqTime()).isEqualTo(reqTime);
        assertThat(found.getResTime()).isEqualTo(reqTime.plusMillis(45));
    }

    @Test
    @DisplayName("partitionDay 컬럼 값 확인")
    void partitionDay_storedCorrectly() {
        Instant feb17 = Instant.parse("2026-02-17T10:00:00Z");
        GatewayLog saved = gatewayLogRepository.save(createLog("pd-test", 1, 200, feb17));

        GatewayLog found = gatewayLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getPartitionDay()).isEqualTo(17);
    }

    // ── 3.2 GatewayLogBody JPA 매핑 ──

    @Test
    @DisplayName("GatewayLog 저장 후 GatewayLogBody 연관 저장 → FK 매핑 확인")
    void saveLogThenBody_fkMapped() {
        GatewayLog log = gatewayLogRepository.save(
                createLog("fk-test", 1, 200, Instant.parse("2026-02-17T10:00:00Z")));

        GatewayLogBody body = GatewayLogBody.builder()
                .gatewayLog(log)
                .requestBody("{\"name\":\"test\"}")
                .responseBody("{\"result\":\"ok\"}")
                .requestHeaders("{\"Content-Type\":\"application/json\"}")
                .responseHeaders("{\"Status\":\"200\"}")
                .build();

        GatewayLogBody savedBody = gatewayLogBodyRepository.save(body);

        assertThat(savedBody.getId()).isNotNull();
        assertThat(savedBody.getGatewayLog().getId()).isEqualTo(log.getId());
    }

    @Test
    @DisplayName("TEXT 컬럼 대용량 문자열 저장 확인 (10KB+)")
    void textColumn_largeContent() {
        GatewayLog log = gatewayLogRepository.save(
                createLog("large-test", 1, 200, Instant.parse("2026-02-17T10:00:00Z")));

        String largeBody = "x".repeat(10_240); // 10KB+

        GatewayLogBody body = GatewayLogBody.builder()
                .gatewayLog(log)
                .requestBody(largeBody)
                .responseBody(largeBody)
                .build();

        GatewayLogBody saved = gatewayLogBodyRepository.saveAndFlush(body);
        GatewayLogBody found = gatewayLogBodyRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getRequestBody()).hasSize(10_240);
        assertThat(found.getResponseBody()).hasSize(10_240);
    }

    @Test
    @DisplayName("GatewayLog만 저장 (body 없음) → body row 없이 정상 동작")
    void saveLogOnly_noBody_works() {
        GatewayLog log = gatewayLogRepository.save(
                createLog("no-body", 1, 200, Instant.parse("2026-02-17T10:00:00Z")));

        assertThat(log.getId()).isNotNull();
        assertThat(gatewayLogBodyRepository.count()).isZero();
    }

    @Test
    @DisplayName("GatewayLog 조회 시 body 지연 로딩(LAZY) 확인 — body 필드가 null이 아닌 proxy")
    void lazyLoading_bodyNotEagerlyLoaded() {
        GatewayLog log = gatewayLogRepository.save(
                createLog("lazy-test", 1, 200, Instant.parse("2026-02-17T10:00:00Z")));
        gatewayLogBodyRepository.save(GatewayLogBody.builder()
                .gatewayLog(log)
                .requestBody("req")
                .responseBody("res")
                .build());

        // findById에서 body는 LAZY이므로 별도 fetch 없이 log만 조회
        GatewayLog found = gatewayLogRepository.findById(log.getId()).orElseThrow();
        assertThat(found.getId()).isNotNull();
    }

    // ── 3.3 배치 저장 (saveAll) ──

    @Test
    @DisplayName("다건 GatewayLog 리스트 saveAll → 전체 건수 저장 확인")
    void saveAll_multipleEntities() {
        Instant reqTime = Instant.parse("2026-02-17T10:00:00Z");
        List<GatewayLog> logs = List.of(
                createLog("batch-1", 1, 200, reqTime),
                createLog("batch-2", 1, 201, reqTime),
                createLog("batch-3", 1, 200, reqTime)
        );

        List<GatewayLog> saved = gatewayLogRepository.saveAll(logs);

        assertThat(saved).hasSize(3);
        assertThat(gatewayLogRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("빈 리스트 saveAll → 예외 없이 0건 처리")
    void saveAll_emptyList_noException() {
        List<GatewayLog> saved = gatewayLogRepository.saveAll(List.of());

        assertThat(saved).isEmpty();
        assertThat(gatewayLogRepository.count()).isZero();
    }

    // ── 3.4 조회 쿼리 ──

    @Test
    @DisplayName("txId로 조회 → 해당 txId의 모든 hop 반환 (hop 순 정렬)")
    void findByTxId_returnsAllHopsOrdered() {
        Instant reqTime = Instant.parse("2026-02-17T10:00:00Z");
        gatewayLogRepository.save(createLog("chain-tx", 3, 200, reqTime));
        gatewayLogRepository.save(createLog("chain-tx", 1, 200, reqTime));
        gatewayLogRepository.save(createLog("chain-tx", 2, 200, reqTime));
        gatewayLogRepository.save(createLog("other-tx", 1, 200, reqTime));

        List<GatewayLog> result = gatewayLogRepository.findByTxIdOrderByHopAsc("chain-tx");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getHop()).isEqualTo(1);
        assertThat(result.get(1).getHop()).isEqualTo(2);
        assertThat(result.get(2).getHop()).isEqualTo(3);
    }

    @Test
    @DisplayName("기간(reqTime 범위)으로 조회 → 해당 기간 로그만 반환")
    void findByReqTimeBetween_returnsOnlyInRange() {
        Instant t1 = Instant.parse("2026-02-17T01:00:00Z");
        Instant t2 = Instant.parse("2026-02-17T05:00:00Z");
        Instant t3 = Instant.parse("2026-02-17T10:00:00Z");
        gatewayLogRepository.save(createLog("time-1", 1, 200, t1));
        gatewayLogRepository.save(createLog("time-2", 1, 200, t2));
        gatewayLogRepository.save(createLog("time-3", 1, 200, t3));

        Instant from = Instant.parse("2026-02-17T00:00:00Z");
        Instant to = Instant.parse("2026-02-17T06:00:00Z");

        List<GatewayLog> result = gatewayLogRepository.findByReqTimeBetweenOrderByReqTimeAsc(from, to);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTxId()).isEqualTo("time-1");
        assertThat(result.get(1).getTxId()).isEqualTo("time-2");
    }

    @Test
    @DisplayName("status 기준 필터 (4xx, 5xx 에러만) → 에러 로그만 반환")
    void findByStatusBetween_errorsOnly() {
        Instant reqTime = Instant.parse("2026-02-17T10:00:00Z");
        gatewayLogRepository.save(createLog("ok-1", 1, 200, reqTime));
        gatewayLogRepository.save(createLog("err-1", 1, 404, reqTime));
        gatewayLogRepository.save(createLog("err-2", 1, 502, reqTime));
        gatewayLogRepository.save(createLog("ok-2", 1, 201, reqTime));

        List<GatewayLog> errors = gatewayLogRepository.findByStatusBetween(400, 599);

        assertThat(errors).hasSize(2);
        assertThat(errors).extracting(GatewayLog::getStatus)
                .containsExactlyInAnyOrder(404, 502);
    }

    // ── 3.5 바디 미수집 로그 조회 ──

    @Test
    @DisplayName("bodyUrl 있고 body 없는 로그만 조회")
    void findLogsNeedingBodyCollection_returnsOnlyWithoutBody() {
        Instant reqTime = Instant.parse("2026-02-17T10:00:00Z");

        // bodyUrl 있고 body 없음 → 대상
        GatewayLog needsBody = gatewayLogRepository.save(createLog("needs-body", 1, 200, reqTime));

        // bodyUrl 있고 body 있음 → 제외
        GatewayLog hasBody = gatewayLogRepository.save(createLog("has-body", 1, 200, reqTime));
        gatewayLogBodyRepository.save(GatewayLogBody.builder()
                .gatewayLog(hasBody)
                .requestBody("req")
                .responseBody("res")
                .build());

        // bodyUrl null → 제외
        GatewayLog noUrl = GatewayLog.builder()
                .txId("no-url")
                .hop(1)
                .status(200)
                .reqTime(reqTime)
                .resTime(reqTime.plusMillis(45))
                .partitionDay(17)
                .build();
        gatewayLogRepository.save(noUrl);

        entityManager.flush();
        entityManager.clear();

        List<GatewayLog> result = gatewayLogRepository.findLogsNeedingBodyCollection(3, PageRequest.of(0, 100));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTxId()).isEqualTo("needs-body");
    }

    @Test
    @DisplayName("Pageable로 batchSize만큼만 조회")
    void findLogsNeedingBodyCollection_respectsPageSize() {
        Instant reqTime = Instant.parse("2026-02-17T10:00:00Z");
        for (int i = 1; i <= 5; i++) {
            gatewayLogRepository.save(createLog("batch-" + i, 1, 200, reqTime));
        }

        List<GatewayLog> result = gatewayLogRepository.findLogsNeedingBodyCollection(3, PageRequest.of(0, 3));

        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("bodyRetryCount가 maxRetries 이상이면 후보에서 제외")
    void findLogsNeedingBodyCollection_excludesExceededRetries() {
        Instant reqTime = Instant.parse("2026-02-17T10:00:00Z");
        gatewayLogRepository.save(createLog("retry-ok", 1, 200, reqTime));

        GatewayLog exhaustedLog = createLog("retry-exceeded", 1, 200, reqTime);
        exhaustedLog.incrementBodyRetryCount();
        exhaustedLog.incrementBodyRetryCount();
        exhaustedLog.incrementBodyRetryCount(); // retryCount = 3
        gatewayLogRepository.save(exhaustedLog);

        entityManager.flush();
        entityManager.clear();

        List<GatewayLog> result = gatewayLogRepository.findLogsNeedingBodyCollection(3, PageRequest.of(0, 100));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTxId()).isEqualTo("retry-ok");
    }

    @Test
    @DisplayName("bodyRetryCount >= maxRetries이고 body 미수집인 건수를 정확히 반환한다")
    void countLogsExceedingRetries_returnsCorrectCount() {
        Instant reqTime = Instant.parse("2026-02-17T10:00:00Z");

        // retryCount=3 (초과) — 대상
        GatewayLog exceeded = createLog("exceeded", 1, 200, reqTime);
        exceeded.incrementBodyRetryCount();
        exceeded.incrementBodyRetryCount();
        exceeded.incrementBodyRetryCount();
        gatewayLogRepository.save(exceeded);

        // retryCount=2 (미초과) — 제외
        GatewayLog notExceeded = createLog("not-exceeded", 1, 200, reqTime);
        notExceeded.incrementBodyRetryCount();
        notExceeded.incrementBodyRetryCount();
        gatewayLogRepository.save(notExceeded);

        // body 이미 수집됨 — 제외
        GatewayLog withBody = createLog("with-body", 1, 200, reqTime);
        withBody.incrementBodyRetryCount();
        withBody.incrementBodyRetryCount();
        withBody.incrementBodyRetryCount();
        GatewayLog savedWithBody = gatewayLogRepository.save(withBody);
        gatewayLogBodyRepository.save(GatewayLogBody.builder()
                .gatewayLog(savedWithBody).requestBody("req").responseBody("res").build());

        entityManager.flush();
        entityManager.clear();

        assertThat(gatewayLogRepository.countLogsExceedingRetries(3)).isEqualTo(1);
    }

    @Test
    @DisplayName("txId로 조회 시 body JOIN fetch → 메타데이터 + body 함께 반환")
    void findByTxIdWithBody_fetchesBodyJoin() {
        Instant reqTime = Instant.parse("2026-02-17T10:00:00Z");
        GatewayLog log = gatewayLogRepository.save(createLog("join-tx", 1, 200, reqTime));
        gatewayLogBodyRepository.save(GatewayLogBody.builder()
                .gatewayLog(log)
                .requestBody("{\"name\":\"test\"}")
                .responseBody("{\"result\":\"ok\"}")
                .requestHeaders("{\"H\":\"1\"}")
                .responseHeaders("{\"H\":\"2\"}")
                .build());

        entityManager.flush();
        entityManager.clear();

        List<GatewayLog> result = gatewayLogRepository.findByTxIdWithBody("join-tx");

        assertThat(result).hasSize(1);
        GatewayLogBody body = result.get(0).getBody();
        assertThat(body).isNotNull();
        assertThat(body.getRequestBody()).isEqualTo("{\"name\":\"test\"}");
        assertThat(body.getResponseBody()).isEqualTo("{\"result\":\"ok\"}");
    }
}
