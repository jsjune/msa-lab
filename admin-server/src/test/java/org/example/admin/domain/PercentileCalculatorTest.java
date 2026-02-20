package org.example.admin.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PercentileCalculatorTest {

    @Test
    @DisplayName("홀수 개수 리스트에서 p50 계산 — [10, 20, 30, 40, 50] → p50 = 30")
    void p50_oddCount() {
        List<Long> values = List.of(10L, 20L, 30L, 40L, 50L);

        Long result = PercentileCalculator.p50(values);

        assertThat(result).isEqualTo(30L);
    }

    @Test
    @DisplayName("짝수 개수 리스트에서 p50 계산 — [10, 20, 30, 40] → p50 = 25 (보간값)")
    void p50_evenCount() {
        List<Long> values = List.of(10L, 20L, 30L, 40L);

        Long result = PercentileCalculator.p50(values);

        assertThat(result).isEqualTo(25L);
    }

    @Test
    @DisplayName("p90 계산 — [1..100] → p90 = 90 근처 값")
    void p90_hundredElements() {
        List<Long> values = java.util.stream.LongStream.rangeClosed(1, 100).boxed().toList();

        Long result = PercentileCalculator.p90(values);

        assertThat(result).isBetween(89L, 91L);
    }

    @Test
    @DisplayName("p95, p99 계산 정확성 — [1..100]")
    void p95_p99_hundredElements() {
        List<Long> values = java.util.stream.LongStream.rangeClosed(1, 100).boxed().toList();

        Long p95 = PercentileCalculator.p95(values);
        Long p99 = PercentileCalculator.p99(values);

        assertThat(p95).isBetween(94L, 96L);
        assertThat(p99).isBetween(98L, 100L);
    }

    @Test
    @DisplayName("빈 리스트 → 모든 백분위 null")
    void emptyList_returnsNull() {
        List<Long> values = List.of();

        assertThat(PercentileCalculator.p50(values)).isNull();
        assertThat(PercentileCalculator.p90(values)).isNull();
        assertThat(PercentileCalculator.p95(values)).isNull();
        assertThat(PercentileCalculator.p99(values)).isNull();
    }

    @Test
    @DisplayName("단일 요소 리스트 → 모든 백분위 = 해당 값")
    void singleElement_returnsSameValue() {
        List<Long> values = List.of(42L);

        assertThat(PercentileCalculator.p50(values)).isEqualTo(42L);
        assertThat(PercentileCalculator.p75(values)).isEqualTo(42L);
        assertThat(PercentileCalculator.p90(values)).isEqualTo(42L);
        assertThat(PercentileCalculator.p95(values)).isEqualTo(42L);
        assertThat(PercentileCalculator.p99(values)).isEqualTo(42L);
    }
}
