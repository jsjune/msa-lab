package org.example.admin.service;

import org.example.admin.domain.DateRange;
import org.example.admin.domain.GatewayLog;
import org.example.admin.domain.GatewayLogBody;
import org.example.admin.domain.TraceDetail;
import org.example.admin.repository.GatewayLogReadRepository;
import org.example.admin.repository.TraceSummaryProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TraceServiceTest {

    @Mock
    private GatewayLogReadRepository logRepository;

    @InjectMocks
    private TraceService traceService;

    @Test
    @DisplayName("txId로 TraceDetail 조회 → hop 체인 + totalDuration 계산")
    void getTrace_returnsTraceDetail() {
        Instant req1 = Instant.parse("2026-02-20T10:00:00.000Z");
        Instant res2 = Instant.parse("2026-02-20T10:00:00.500Z");

        given(logRepository.findByTxIdOrderByHop("tx-1")).willReturn(List.of(
                buildLog("tx-1", 1, "/server-a/chain", 200, 200L, req1, req1.plusMillis(200)),
                buildLog("tx-1", 2, "/server-b/chain", 200, 100L, req1.plusMillis(250), res2)
        ));

        TraceDetail detail = traceService.getTrace("tx-1", false);

        assertThat(detail.getTxId()).isEqualTo("tx-1");
        assertThat(detail.getHopCount()).isEqualTo(2);
        assertThat(detail.getTotalDuration()).isEqualTo(500L);
        assertThat(detail.getHops()).extracting("hop").containsExactly(1, 2);
    }

    @Test
    @DisplayName("트레이스 검색 — 기간 필터 → 페이징된 txId+reqTime 목록")
    void searchTraces_byPeriod() {
        DateRange range = DateRange.lastHours(24);
        PageRequest pageable = PageRequest.of(0, 10);
        TraceSummaryProjection p1 = mockProjection("tx-1");
        TraceSummaryProjection p2 = mockProjection("tx-2");
        given(logRepository.findDistinctTxIds(any(), any(), eq(pageable)))
                .willReturn(new PageImpl<>(List.of(p1, p2)));

        Page<TraceSummaryProjection> result = traceService.searchTraces(range, null, null, pageable);

        assertThat(result.getContent()).extracting(TraceSummaryProjection::getTxId)
                .containsExactly("tx-1", "tx-2");
    }

    @Test
    @DisplayName("트레이스 검색 — path 필터 적용")
    void searchTraces_byPath() {
        DateRange range = DateRange.lastHours(24);
        PageRequest pageable = PageRequest.of(0, 10);
        TraceSummaryProjection p = mockProjection("tx-1");
        given(logRepository.findDistinctTxIdsByPath(any(), any(), eq("/server-a/chain"), eq(pageable)))
                .willReturn(new PageImpl<>(List.of(p)));

        Page<TraceSummaryProjection> result = traceService.searchTraces(range, "/server-a/chain", null, pageable);

        assertThat(result.getContent()).extracting(TraceSummaryProjection::getTxId)
                .containsExactly("tx-1");
    }

    @Test
    @DisplayName("트레이스 검색 — status 에러 필터 적용")
    void searchTraces_byError() {
        DateRange range = DateRange.lastHours(24);
        PageRequest pageable = PageRequest.of(0, 10);
        TraceSummaryProjection p = mockProjection("tx-err");
        given(logRepository.findDistinctTxIdsByError(any(), any(), eq(pageable)))
                .willReturn(new PageImpl<>(List.of(p)));

        Page<TraceSummaryProjection> result = traceService.searchTraces(range, null, "5xx", pageable);

        assertThat(result.getContent()).extracting(TraceSummaryProjection::getTxId)
                .containsExactly("tx-err");
    }

    private TraceSummaryProjection mockProjection(String txId) {
        Instant reqTime = Instant.parse("2026-02-20T10:00:00Z");
        return new TraceSummaryProjection() {
            @Override public String getTxId() { return txId; }
            @Override public Instant getReqTime() { return reqTime; }
        };
    }

    @Test
    @DisplayName("상세 포함 옵션 → hop에 headers + body 모두 포함")
    void getTrace_withDetail() {
        Instant req = Instant.parse("2026-02-20T10:00:00.000Z");
        GatewayLog log = buildLog("tx-1", 1, "/server-a/chain", 200, 100L, req, req.plusMillis(100));
        GatewayLogBody body = GatewayLogBody.builder()
                .gatewayLog(log)
                .requestHeaders("Content-Type: application/json")
                .requestBody("{\"req\":1}")
                .responseHeaders("Content-Type: application/json")
                .responseBody("{\"res\":1}")
                .build();
        // Use reflection to set body on log since it's mapped by JPA
        try {
            var bodyField = GatewayLog.class.getDeclaredField("body");
            bodyField.setAccessible(true);
            bodyField.set(log, body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        given(logRepository.findByTxIdWithBody("tx-1")).willReturn(List.of(log));

        TraceDetail detail = traceService.getTrace("tx-1", true);

        assertThat(detail.getHops().getFirst().getRequestHeaders()).isEqualTo("Content-Type: application/json");
        assertThat(detail.getHops().getFirst().getRequestBody()).isEqualTo("{\"req\":1}");
        assertThat(detail.getHops().getFirst().getResponseHeaders()).isEqualTo("Content-Type: application/json");
        assertThat(detail.getHops().getFirst().getResponseBody()).isEqualTo("{\"res\":1}");
    }

    private GatewayLog buildLog(String txId, int hop, String path, int status, Long durationMs,
                                 Instant reqTime, Instant resTime) {
        return GatewayLog.builder()
                .txId(txId).hop(hop).path(path).target("http://localhost" + path)
                .status(status).durationMs(durationMs)
                .reqTime(reqTime).resTime(resTime)
                .partitionDay(20).build();
    }
}
