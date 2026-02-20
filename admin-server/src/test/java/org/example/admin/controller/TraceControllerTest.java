package org.example.admin.controller;

import org.example.admin.domain.TraceDetail;
import org.example.admin.domain.TraceHop;
import org.example.admin.repository.TraceSummaryProjection;
import org.example.admin.service.TraceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TraceController.class)
class TraceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TraceService traceService;

    @Test
    @DisplayName("GET /api/traces/{txId} → 단건 TraceDetail 조회")
    void getTrace() throws Exception {
        Instant req = Instant.parse("2026-02-20T10:00:00Z");
        TraceDetail detail = new TraceDetail("tx-1", List.of(
                TraceHop.builder().txId("tx-1").hop(1).path("/a").status(200)
                        .reqTime(req).resTime(req.plusMillis(100)).build()
        ));
        given(traceService.getTrace("tx-1", false)).willReturn(detail);

        mockMvc.perform(get("/api/traces/tx-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txId").value("tx-1"))
                .andExpect(jsonPath("$.hopCount").value(1));
    }

    @Test
    @DisplayName("GET /api/traces/{txId}?includeDetail=true → headers + body 포함 조회")
    void getTrace_withDetail() throws Exception {
        Instant req = Instant.parse("2026-02-20T10:00:00Z");
        TraceDetail detail = new TraceDetail("tx-1", List.of(
                TraceHop.builder().txId("tx-1").hop(1).path("/a").status(200)
                        .reqTime(req).resTime(req.plusMillis(100))
                        .requestHeaders("Content-Type: application/json")
                        .requestBody("{\"key\":1}")
                        .responseHeaders("Content-Type: application/json")
                        .responseBody("{\"ok\":true}")
                        .build()
        ));
        given(traceService.getTrace("tx-1", true)).willReturn(detail);

        mockMvc.perform(get("/api/traces/tx-1").param("includeDetail", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hops[0].requestHeaders").value("Content-Type: application/json"))
                .andExpect(jsonPath("$.hops[0].requestBody").value("{\"key\":1}"));
    }

    @Test
    @DisplayName("GET /api/traces?from=...&to=... → txId + reqTime 목록 검색 (페이징)")
    void searchTraces() throws Exception {
        Instant req = Instant.parse("2026-02-20T10:00:00Z");
        TraceSummaryProjection p1 = mockProjection("tx-1", req);
        TraceSummaryProjection p2 = mockProjection("tx-2", req.plusMillis(500));
        given(traceService.searchTraces(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(p1, p2), PageRequest.of(0, 20), 2));

        mockMvc.perform(get("/api/traces")
                        .param("from", "2026-02-20T00:00:00Z")
                        .param("to", "2026-02-20T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].txId").value("tx-1"))
                .andExpect(jsonPath("$.content[1].txId").value("tx-2"));
    }

    @Test
    @DisplayName("GET /api/traces?status=5xx → 에러 트레이스만 검색")
    void searchTraces_errorOnly() throws Exception {
        TraceSummaryProjection p = mockProjection("tx-err", Instant.parse("2026-02-20T10:00:00Z"));
        given(traceService.searchTraces(any(), any(), eq("5xx"), any()))
                .willReturn(new PageImpl<>(List.of(p)));

        mockMvc.perform(get("/api/traces")
                        .param("status", "5xx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].txId").value("tx-err"));
    }

    private TraceSummaryProjection mockProjection(String txId, Instant reqTime) {
        return new TraceSummaryProjection() {
            @Override public String getTxId() { return txId; }
            @Override public Instant getReqTime() { return reqTime; }
        };
    }

    @Test
    @DisplayName("존재하지 않는 txId → 404 Not Found")
    void getTrace_notFound() throws Exception {
        given(traceService.getTrace("non-existent", false))
                .willReturn(new TraceDetail("non-existent", List.of()));

        mockMvc.perform(get("/api/traces/non-existent"))
                .andExpect(status().isNotFound());
    }
}
