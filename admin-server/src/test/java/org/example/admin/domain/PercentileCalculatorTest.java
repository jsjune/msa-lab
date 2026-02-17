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
}
