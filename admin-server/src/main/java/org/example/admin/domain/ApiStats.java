package org.example.admin.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiStats {

    private String path;
    private long count;
    private long errorCount;
    private double errorRate;
    private Long avg;
    private Long min;
    private Long max;
    private Long p30;
    private Long p50;
    private Long p75;
    private Long p90;
    private Long p95;
    private Long p99;

    public static double calculateErrorRate(long errorCount, long count) {
        if (count == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(errorCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}