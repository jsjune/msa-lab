package org.example.admin.service;

import org.example.admin.domain.DateRange;
import org.example.admin.domain.TrafficGraph;
import org.example.admin.repository.GatewayLogReadRepository;
import org.example.admin.repository.HopRawProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ServiceGraphServiceTest {

    @Mock
    private GatewayLogReadRepository logRepository;

    @InjectMocks
    private ServiceGraphService serviceGraphService;

    private final DateRange range = DateRange.of(
            Instant.parse("2026-02-20T00:00:00Z"),
            Instant.parse("2026-02-20T23:59:59Z"));

    @Test
    @DisplayName("buildGraph(DateRange) → TrafficGraph 반환")
    void buildGraph_returnsTrafficGraph() {
        given(logRepository.findHopRawData(any(), any())).willReturn(List.of(
                hop("tx-1", 1, "/server-a/chain", 200, 100L)
        ));

        TrafficGraph graph = serviceGraphService.buildGraph(range);

        assertThat(graph).isNotNull();
        assertThat(graph.getNodes()).isNotEmpty();
        assertThat(graph.getEdges()).isNotEmpty();
    }

    @Test
    @DisplayName("기간 내 데이터 없음 → 빈 그래프 (nodes=[], edges=[])")
    void buildGraph_emptyWhenNoData() {
        given(logRepository.findHopRawData(any(), any())).willReturn(List.of());

        TrafficGraph graph = serviceGraphService.buildGraph(range);

        assertThat(graph.getNodes()).isEmpty();
        assertThat(graph.getEdges()).isEmpty();
    }

    @Test
    @DisplayName("노드별 inbound requestCount, errorRate, avgDuration 집계")
    void buildGraph_nodeAggregation() {
        given(logRepository.findHopRawData(any(), any())).willReturn(List.of(
                hop("tx-1", 1, "/server-a/chain", 200, 100L),
                hop("tx-1", 2, "/server-b/chain", 200,  50L),
                hop("tx-2", 1, "/server-a/chain", 500, 200L)
        ));

        TrafficGraph graph = serviceGraphService.buildGraph(range);

        var serverA = graph.getNodes().stream()
                .filter(n -> "server-a".equals(n.getName())).findFirst().orElseThrow();
        assertThat(serverA.getRequestCount()).isEqualTo(2);
        assertThat(serverA.getErrorRate()).isEqualTo(50.0);
        assertThat(serverA.getAvgDuration()).isEqualTo(150L); // (100+200)/2

        var external = graph.getNodes().stream()
                .filter(n -> "external".equals(n.getName())).findFirst().orElseThrow();
        assertThat(external.getRequestCount()).isEqualTo(0);
        assertThat(external.getAvgDuration()).isNull();
    }

    @Test
    @DisplayName("엣지별 requestCount, errorRate, p50, p99 집계")
    void buildGraph_edgeAggregation() {
        // external→server-a 엣지를 3개 tx가 통과 (1개 에러, duration 100/200/300)
        given(logRepository.findHopRawData(any(), any())).willReturn(List.of(
                hop("tx-1", 1, "/server-a/chain", 200, 100L),
                hop("tx-2", 1, "/server-a/chain", 500, 200L),
                hop("tx-3", 1, "/server-a/chain", 200, 300L)
        ));

        TrafficGraph graph = serviceGraphService.buildGraph(range);

        assertThat(graph.getEdges()).hasSize(1);
        var edge = graph.getEdges().getFirst();
        assertThat(edge.getSource()).isEqualTo("external");
        assertThat(edge.getTarget()).isEqualTo("server-a");
        assertThat(edge.getRequestCount()).isEqualTo(3);
        assertThat(edge.getErrorRate()).isEqualTo(33.33); // 1/3 * 100
        assertThat(edge.getP50()).isEqualTo(200L);        // 중앙값
        assertThat(edge.getP99()).isEqualTo(298L);        // 0.99 * (300-200) + 200
    }

    @Test
    @DisplayName("hop=1 source는 \"external\", hop>1 source는 이전 hop의 path prefix에서 추론")
    void buildGraph_sourceInference() {
        given(logRepository.findHopRawData(any(), any())).willReturn(List.of(
                hop("tx-1", 1, "/server-a/chain", 200, 100L),
                hop("tx-1", 2, "/server-b/chain", 200, 50L)
        ));

        TrafficGraph graph = serviceGraphService.buildGraph(range);

        assertThat(graph.getEdges()).anySatisfy(e -> {
            assertThat(e.getSource()).isEqualTo("external");  // hop=1
            assertThat(e.getTarget()).isEqualTo("server-a");
        });
        assertThat(graph.getEdges()).anySatisfy(e -> {
            assertThat(e.getSource()).isEqualTo("server-a"); // hop=2, 이전 hop target
            assertThat(e.getTarget()).isEqualTo("server-b");
        });
    }

    @Test
    @DisplayName("동일 txId 내 hop 순서로 source→target 엣지 구성")
    void buildGraph_edgesBuiltFromHopChain() {
        given(logRepository.findHopRawData(any(), any())).willReturn(List.of(
                hop("tx-1", 1, "/server-a/chain", 200, 100L),
                hop("tx-1", 2, "/server-b/chain", 200, 50L),
                hop("tx-1", 3, "/server-c/chain", 200, 30L)
        ));

        TrafficGraph graph = serviceGraphService.buildGraph(range);

        assertThat(graph.getEdges()).hasSize(3);
        assertThat(graph.getEdges()).extracting(e -> e.getSource() + "→" + e.getTarget())
                .containsExactlyInAnyOrder(
                        "external→server-a",
                        "server-a→server-b",
                        "server-b→server-c"
                );
    }

    // ---- 헬퍼 ----

    private HopRawProjection hop(String txId, int hop, String path, int status, Long durationMs) {
        return new HopRawProjection() {
            @Override public String getTxId() { return txId; }
            @Override public int getHop() { return hop; }
            @Override public String getPath() { return path; }
            @Override public int getStatus() { return status; }
            @Override public Long getDurationMs() { return durationMs; }
        };
    }
}
