package org.example.admin.domain;

import java.util.Collections;
import java.util.List;

public class PercentileCalculator {

    public static Long calculate(List<Long> sortedValues, double percentile) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return null;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }

        List<Long> sorted = sortedValues.stream().sorted().toList();
        double index = percentile / 100.0 * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);

        if (lower == upper) {
            return sorted.get(lower);
        }

        double fraction = index - lower;
        return Math.round(sorted.get(lower) + fraction * (sorted.get(upper) - sorted.get(lower)));
    }

    public static Long p50(List<Long> values) {
        return calculate(values, 50);
    }

    public static Long p75(List<Long> values) {
        return calculate(values, 75);
    }

    public static Long p90(List<Long> values) {
        return calculate(values, 90);
    }

    public static Long p95(List<Long> values) {
        return calculate(values, 95);
    }

    public static Long p99(List<Long> values) {
        return calculate(values, 99);
    }
}
