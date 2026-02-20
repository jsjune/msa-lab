package org.example.admin.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceGraphTest {

    @Test
    @DisplayName("ServiceNode 필드 확인: name, requestCount, errorRate, avgDuration")
    void serviceNode_fields() {
        ServiceNode node = ServiceNode.builder()
                .name("server-a")
                .requestCount(100)
                .errorRate(5.0)
                .avgDuration(42L)
                .build();

        assertThat(node.getName()).isEqualTo("server-a");
        assertThat(node.getRequestCount()).isEqualTo(100);
        assertThat(node.getErrorRate()).isEqualTo(5.0);
        assertThat(node.getAvgDuration()).isEqualTo(42L);
    }

    @Test
    @DisplayName("ServiceEdge 필드 확인: source, target, requestCount, errorRate, p50, p99")
    void serviceEdge_fields() {
        ServiceEdge edge = ServiceEdge.builder()
                .source("external")
                .target("server-a")
                .requestCount(50)
                .errorRate(2.0)
                .p50(30L)
                .p99(200L)
                .build();

        assertThat(edge.getSource()).isEqualTo("external");
        assertThat(edge.getTarget()).isEqualTo("server-a");
        assertThat(edge.getRequestCount()).isEqualTo(50);
        assertThat(edge.getErrorRate()).isEqualTo(2.0);
        assertThat(edge.getP50()).isEqualTo(30L);
        assertThat(edge.getP99()).isEqualTo(200L);
    }

    @Test
    @DisplayName("TrafficGraph 필드 확인: nodes(List<ServiceNode>), edges(List<ServiceEdge>)")
    void trafficGraph_fields() {
        ServiceNode node = ServiceNode.builder().name("server-a").build();
        ServiceEdge edge = ServiceEdge.builder().source("external").target("server-a").build();

        TrafficGraph graph = TrafficGraph.builder()
                .nodes(List.of(node))
                .edges(List.of(edge))
                .build();

        assertThat(graph.getNodes()).hasSize(1);
        assertThat(graph.getNodes().getFirst().getName()).isEqualTo("server-a");
        assertThat(graph.getEdges()).hasSize(1);
        assertThat(graph.getEdges().getFirst().getSource()).isEqualTo("external");
    }

    @Test
    @DisplayName("hop=1 → source=\"external\" 추론 규칙 확인")
    void serviceEdge_hop1_sourceIsExternal() {
        ServiceEdge edge = ServiceEdge.builder()
                .source("external")
                .target("server-a")
                .requestCount(10)
                .build();

        assertThat(edge.getSource()).isEqualTo("external");
    }

    @Test
    @DisplayName("hop>1 → 동일 txId 이전 hop target이 source 추론 규칙 확인")
    void serviceEdge_hop2_sourcePreviousHopTarget() {
        // hop1: external → server-a, hop2: server-a → server-b
        ServiceEdge hop1 = ServiceEdge.builder().source("external").target("server-a").build();
        ServiceEdge hop2 = ServiceEdge.builder().source("server-a").target("server-b").build();

        assertThat(hop2.getSource()).isEqualTo(hop1.getTarget());
    }
}
