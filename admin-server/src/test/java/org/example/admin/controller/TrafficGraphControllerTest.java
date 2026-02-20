package org.example.admin.controller;

import org.example.admin.domain.DateRange;
import org.example.admin.domain.ServiceEdge;
import org.example.admin.domain.ServiceNode;
import org.example.admin.domain.TrafficGraph;
import org.example.admin.service.ServiceGraphService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrafficGraphController.class)
class TrafficGraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServiceGraphService serviceGraphService;

    @Test
    @DisplayName("기간 미지정 → 기본 최근 24시간 적용 (200 OK)")
    void getGraph_defaultPeriod() throws Exception {
        given(serviceGraphService.buildGraph(any(DateRange.class)))
                .willReturn(TrafficGraph.builder().nodes(List.of()).edges(List.of()).build());

        mockMvc.perform(get("/api/traffic/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.edges").isArray());
    }

    @Test
    @DisplayName("from > to → 400 Bad Request")
    void getGraph_fromAfterTo_badRequest() throws Exception {
        mockMvc.perform(get("/api/traffic/graph")
                        .param("from", "2026-02-20T23:59:59Z")
                        .param("to",   "2026-02-20T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("30일 초과 기간 → 400 Bad Request")
    void getGraph_exceedsMaxPeriod_badRequest() throws Exception {
        mockMvc.perform(get("/api/traffic/graph")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to",   "2026-02-20T23:59:59Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("데이터 없음 → { nodes: [], edges: [] } (200 OK)")
    void getGraph_emptyData_returnsEmptyGraph() throws Exception {
        given(serviceGraphService.buildGraph(any(DateRange.class)))
                .willReturn(TrafficGraph.builder().nodes(List.of()).edges(List.of()).build());

        mockMvc.perform(get("/api/traffic/graph")
                        .param("from", "2026-02-20T00:00:00Z")
                        .param("to",   "2026-02-20T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isEmpty())
                .andExpect(jsonPath("$.edges").isEmpty());
    }

    @Test
    @DisplayName("GET /api/traffic/graph — TrafficGraph JSON 반환 (nodes + edges)")
    void getGraph_returnsTrafficGraph() throws Exception {
        given(serviceGraphService.buildGraph(any(DateRange.class))).willReturn(
                TrafficGraph.builder()
                        .nodes(List.of(
                                ServiceNode.builder().name("external").requestCount(0).errorRate(0.0).build(),
                                ServiceNode.builder().name("server-a").requestCount(2).errorRate(0.0).avgDuration(100L).build()
                        ))
                        .edges(List.of(
                                ServiceEdge.builder().source("external").target("server-a")
                                        .requestCount(2).errorRate(0.0).p50(100L).p99(100L).build()
                        ))
                        .build()
        );

        mockMvc.perform(get("/api/traffic/graph")
                        .param("from", "2026-02-20T00:00:00Z")
                        .param("to",   "2026-02-20T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes[0].name").value("external"))
                .andExpect(jsonPath("$.nodes[1].name").value("server-a"))
                .andExpect(jsonPath("$.edges").isArray())
                .andExpect(jsonPath("$.edges[0].source").value("external"))
                .andExpect(jsonPath("$.edges[0].target").value("server-a"))
                .andExpect(jsonPath("$.edges[0].requestCount").value(2));
    }
}
