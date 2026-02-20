package org.example.admin.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceDetailTest {

    @Test
    @DisplayName("TraceHop 필드 보유 확인 — 모든 필드 접근 가능")
    void traceHop_fieldsAccessible() {
        Instant req = Instant.parse("2026-02-20T10:00:00Z");
        Instant res = Instant.parse("2026-02-20T10:00:00.100Z");

        TraceHop hop = TraceHop.builder()
                .txId("tx-1")
                .hop(1)
                .path("/server-a/chain")
                .target("http://localhost:8081/chain")
                .status(200)
                .durationMs(100L)
                .reqTime(req)
                .resTime(res)
                .error(null)
                .build();

        assertThat(hop.getTxId()).isEqualTo("tx-1");
        assertThat(hop.getHop()).isEqualTo(1);
        assertThat(hop.getPath()).isEqualTo("/server-a/chain");
        assertThat(hop.getTarget()).isEqualTo("http://localhost:8081/chain");
        assertThat(hop.getStatus()).isEqualTo(200);
        assertThat(hop.getDurationMs()).isEqualTo(100L);
        assertThat(hop.getReqTime()).isEqualTo(req);
        assertThat(hop.getResTime()).isEqualTo(res);
        assertThat(hop.getError()).isNull();
    }

    @Test
    @DisplayName("TraceHop hop 순서 정렬 가능 확인")
    void traceHop_comparable() {
        TraceHop hop1 = TraceHop.builder().hop(3).build();
        TraceHop hop2 = TraceHop.builder().hop(1).build();
        TraceHop hop3 = TraceHop.builder().hop(2).build();

        List<TraceHop> sorted = List.of(hop1, hop2, hop3).stream().sorted().toList();

        assertThat(sorted).extracting(TraceHop::getHop).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("TraceDetail 필드 보유 확인 — txId, hops, totalDuration, hopCount")
    void traceDetail_fieldsAccessible() {
        Instant req1 = Instant.parse("2026-02-20T10:00:00Z");
        Instant res2 = Instant.parse("2026-02-20T10:00:00.500Z");

        List<TraceHop> hops = List.of(
                TraceHop.builder().hop(1).reqTime(req1).resTime(Instant.parse("2026-02-20T10:00:00.200Z")).build(),
                TraceHop.builder().hop(2).reqTime(Instant.parse("2026-02-20T10:00:00.250Z")).resTime(res2).build()
        );

        TraceDetail detail = new TraceDetail("tx-1", hops);

        assertThat(detail.getTxId()).isEqualTo("tx-1");
        assertThat(detail.getHopCount()).isEqualTo(2);
        assertThat(detail.getTotalDuration()).isEqualTo(500L);
    }

    @Test
    @DisplayName("totalDuration = 첫 hop의 reqTime ~ 마지막 hop의 resTime 차이")
    void traceDetail_totalDuration() {
        Instant req1 = Instant.parse("2026-02-20T10:00:00.000Z");
        Instant res3 = Instant.parse("2026-02-20T10:00:01.200Z");

        List<TraceHop> hops = List.of(
                TraceHop.builder().hop(1).reqTime(req1).resTime(Instant.parse("2026-02-20T10:00:00.300Z")).build(),
                TraceHop.builder().hop(2).reqTime(Instant.parse("2026-02-20T10:00:00.400Z")).resTime(Instant.parse("2026-02-20T10:00:00.800Z")).build(),
                TraceHop.builder().hop(3).reqTime(Instant.parse("2026-02-20T10:00:00.900Z")).resTime(res3).build()
        );

        TraceDetail detail = new TraceDetail("tx-1", hops);

        assertThat(detail.getTotalDuration()).isEqualTo(1200L);
    }

    @Test
    @DisplayName("hops가 hop 순서대로 정렬되어 있는지 확인 — 역순 입력도 정렬됨")
    void traceDetail_hopsSorted() {
        List<TraceHop> hops = List.of(
                TraceHop.builder().hop(3).build(),
                TraceHop.builder().hop(1).build(),
                TraceHop.builder().hop(2).build()
        );

        TraceDetail detail = new TraceDetail("tx-1", hops);

        assertThat(detail.getHops()).extracting(TraceHop::getHop).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("빈 hops 리스트 → totalDuration = 0, hopCount = 0")
    void traceDetail_emptyHops() {
        TraceDetail detail = new TraceDetail("tx-1", List.of());

        assertThat(detail.getHopCount()).isZero();
        assertThat(detail.getTotalDuration()).isZero();
    }
}