package org.example.admin.service;

import org.example.admin.domain.ApiStats;
import org.example.admin.domain.DateRange;
import org.example.admin.repository.GatewayLogReadRepository;
import org.example.admin.repository.PathStatsProjection;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ApiStatsServiceTest {

    @Mock
    private GatewayLogReadRepository logRepository;

    @InjectMocks
    private ApiStatsService apiStatsService;

    private final DateRange range = DateRange.of(
            Instant.parse("2026-02-20T00:00:00Z"),
            Instant.parse("2026-02-20T23:59:59Z"));

    @Test
    @DisplayName("path별 통계 조회 → ApiStats 리스트 반환 (count, errorRate, 백분위 포함)")
    void getStats_returnsApiStatsList() {
        given(logRepository.findPathStats(any(), any())).willReturn(List.of(
                mockProjection("/server-a/chain", 10, 2)
        ));
        given(logRepository.findDurationsByPath(eq("/server-a/chain"), any(), any()))
                .willReturn(List.of(10L, 20L, 30L, 40L, 50L));

        List<ApiStats> stats = apiStatsService.getStats(range);

        assertThat(stats).hasSize(1);
        ApiStats s = stats.getFirst();
        assertThat(s.getPath()).isEqualTo("/server-a/chain");
        assertThat(s.getCount()).isEqualTo(10);
        assertThat(s.getErrorCount()).isEqualTo(2);
        assertThat(s.getErrorRate()).isEqualTo(20.0);
        assertThat(s.getP50()).isEqualTo(30L);
        assertThat(s.getMin()).isEqualTo(10L);
        assertThat(s.getMax()).isEqualTo(50L);
    }

    @Test
    @DisplayName("기간 필터 적용 — repository에 from/to 전달 확인")
    void getStats_periodFilterApplied() {
        given(logRepository.findPathStats(eq(range.getFrom()), eq(range.getTo())))
                .willReturn(List.of());

        List<ApiStats> stats = apiStatsService.getStats(range);

        assertThat(stats).isEmpty();
    }

    @Test
    @DisplayName("백분위 계산 로직이 올바르게 조합되는지 확인")
    void getStats_percentilesCalculated() {
        given(logRepository.findPathStats(any(), any())).willReturn(List.of(
                mockProjection("/api", 5, 0)
        ));
        given(logRepository.findDurationsByPath(eq("/api"), any(), any()))
                .willReturn(List.of(100L, 200L, 300L, 400L, 500L));

        ApiStats s = apiStatsService.getStats(range).getFirst();

        assertThat(s.getP50()).isEqualTo(300L);
        assertThat(s.getP75()).isEqualTo(400L);
        assertThat(s.getP90()).isNotNull();
        assertThat(s.getP95()).isNotNull();
        assertThat(s.getP99()).isNotNull();
    }

    @Test
    @DisplayName("데이터 없는 path → 결과에 미포함")
    void getStats_noData_emptyResult() {
        given(logRepository.findPathStats(any(), any())).willReturn(List.of());

        List<ApiStats> stats = apiStatsService.getStats(range);

        assertThat(stats).isEmpty();
    }

    @Test
    @DisplayName("요청 수 기준 상위 N개 API 반환")
    void getTopByCount() {
        given(logRepository.findPathStats(any(), any())).willReturn(List.of(
                mockProjection("/a", 100, 0),
                mockProjection("/b", 50, 0),
                mockProjection("/c", 200, 0)
        ));
        given(logRepository.findDurationsByPath(any(), any(), any())).willReturn(List.of(10L));

        List<ApiStats> top = apiStatsService.getTopByCount(range, 2);

        assertThat(top).hasSize(2);
        assertThat(top.get(0).getPath()).isEqualTo("/c");
        assertThat(top.get(1).getPath()).isEqualTo("/a");
    }

    @Test
    @DisplayName("에러율 기준 상위 N개 API 반환")
    void getTopByErrorRate() {
        given(logRepository.findPathStats(any(), any())).willReturn(List.of(
                mockProjection("/a", 100, 10),  // 10%
                mockProjection("/b", 50, 25),   // 50%
                mockProjection("/c", 200, 20)   // 10%
        ));
        given(logRepository.findDurationsByPath(any(), any(), any())).willReturn(List.of(10L));

        List<ApiStats> top = apiStatsService.getTopByErrorRate(range, 1);

        assertThat(top).hasSize(1);
        assertThat(top.getFirst().getPath()).isEqualTo("/b");
    }

    @Test
    @DisplayName("p99 기준 상위 N개 API 반환 (가장 느린 API)")
    void getTopByP99() {
        given(logRepository.findPathStats(any(), any())).willReturn(List.of(
                mockProjection("/fast", 10, 0),
                mockProjection("/slow", 10, 0)
        ));
        given(logRepository.findDurationsByPath(eq("/fast"), any(), any()))
                .willReturn(List.of(10L, 20L, 30L));
        given(logRepository.findDurationsByPath(eq("/slow"), any(), any()))
                .willReturn(List.of(500L, 600L, 700L));

        List<ApiStats> top = apiStatsService.getTopByP99(range, 1);

        assertThat(top).hasSize(1);
        assertThat(top.getFirst().getPath()).isEqualTo("/slow");
    }

    private PathStatsProjection mockProjection(String path, long count, long errorCount) {
        return new PathStatsProjection() {
            @Override public String getPath() { return path; }
            @Override public long getCount() { return count; }
            @Override public long getErrorCount() { return errorCount; }
        };
    }
}
