package org.example.admin.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiStatsTest {

    @Test
    @DisplayName("ApiStats 필드 보유 확인 — Builder로 생성 후 모든 필드 접근 가능")
    void fieldsAccessible() {
        ApiStats stats = ApiStats.builder()
                .path("/server-a/chain")
                .count(100)
                .errorCount(5)
                .errorRate(5.0)
                .avg(45L)
                .min(10L)
                .max(200L)
                .p50(40L)
                .p75(60L)
                .p90(80L)
                .p95(120L)
                .p99(190L)
                .build();

        assertThat(stats.getPath()).isEqualTo("/server-a/chain");
        assertThat(stats.getCount()).isEqualTo(100);
        assertThat(stats.getErrorCount()).isEqualTo(5);
        assertThat(stats.getErrorRate()).isEqualTo(5.0);
        assertThat(stats.getAvg()).isEqualTo(45L);
        assertThat(stats.getMin()).isEqualTo(10L);
        assertThat(stats.getMax()).isEqualTo(200L);
        assertThat(stats.getP50()).isEqualTo(40L);
        assertThat(stats.getP75()).isEqualTo(60L);
        assertThat(stats.getP90()).isEqualTo(80L);
        assertThat(stats.getP95()).isEqualTo(120L);
        assertThat(stats.getP99()).isEqualTo(190L);
    }

    @Test
    @DisplayName("errorRate 계산 — errorCount / count * 100 소수점 2자리")
    void errorRate_calculation() {
        double rate = ApiStats.calculateErrorRate(3, 7);

        assertThat(rate).isEqualTo(42.86);
    }

    @Test
    @DisplayName("count = 0 일 때 errorRate = 0 — ZeroDivision 방지")
    void errorRate_zeroDivision() {
        double rate = ApiStats.calculateErrorRate(0, 0);

        assertThat(rate).isEqualTo(0.0);
    }
}