package org.example.admin.controller;

import org.example.admin.domain.ApiStats;
import org.example.admin.domain.DateRange;
import org.example.admin.service.ApiStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiStatsController.class)
class ApiStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiStatsService apiStatsService;

    @Test
    @DisplayName("GET /api/stats — 전체 API 통계 목록")
    void getStats() throws Exception {
        given(apiStatsService.getStats(any(DateRange.class))).willReturn(List.of(
                ApiStats.builder().path("/server-a/chain").count(100).errorRate(5.0).p50(30L).build()
        ));

        mockMvc.perform(get("/api/stats")
                        .param("from", "2026-02-20T00:00:00Z")
                        .param("to", "2026-02-20T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value("/server-a/chain"))
                .andExpect(jsonPath("$[0].count").value(100));
    }

    @Test
    @DisplayName("GET /api/stats?path= — 특정 path 통계 필터")
    void getStats_pathFilter() throws Exception {
        given(apiStatsService.getStats(any(DateRange.class))).willReturn(List.of(
                ApiStats.builder().path("/server-a/chain").count(100).build(),
                ApiStats.builder().path("/server-b/chain").count(50).build()
        ));

        mockMvc.perform(get("/api/stats")
                        .param("from", "2026-02-20T00:00:00Z")
                        .param("to", "2026-02-20T23:59:59Z")
                        .param("path", "/server-a/chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].path").value("/server-a/chain"));
    }

    @Test
    @DisplayName("GET /api/stats/top?by=count — 상위 N개 API")
    void getTop_byCount() throws Exception {
        given(apiStatsService.getTopByCount(any(DateRange.class), anyInt())).willReturn(List.of(
                ApiStats.builder().path("/top").count(999).build()
        ));

        mockMvc.perform(get("/api/stats/top")
                        .param("from", "2026-02-20T00:00:00Z")
                        .param("to", "2026-02-20T23:59:59Z")
                        .param("by", "count")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].count").value(999));
    }

    @Test
    @DisplayName("GET /api/stats/top?by=p99 — 가장 느린 API")
    void getTop_byP99() throws Exception {
        given(apiStatsService.getTopByP99(any(DateRange.class), anyInt())).willReturn(List.of(
                ApiStats.builder().path("/slow").p99(500L).build()
        ));

        mockMvc.perform(get("/api/stats/top")
                        .param("by", "p99")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].p99").value(500));
    }

    @Test
    @DisplayName("기간 미지정 → 기본 최근 24시간 적용 (정상 200)")
    void getStats_noPeriod_defaults() throws Exception {
        given(apiStatsService.getStats(any(DateRange.class))).willReturn(List.of());

        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("from > to → 400 Bad Request")
    void getStats_fromAfterTo_badRequest() throws Exception {
        mockMvc.perform(get("/api/stats")
                        .param("from", "2026-02-20T23:59:59Z")
                        .param("to", "2026-02-20T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("30일 초과 기간 → 400 Bad Request")
    void getStats_exceeds30days_badRequest() throws Exception {
        mockMvc.perform(get("/api/stats")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-02-20T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }
}
