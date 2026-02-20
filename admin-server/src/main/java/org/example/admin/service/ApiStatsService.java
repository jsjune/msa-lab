package org.example.admin.service;

import lombok.RequiredArgsConstructor;
import org.example.admin.domain.ApiStats;
import org.example.admin.domain.DateRange;
import org.example.admin.domain.PercentileCalculator;
import org.example.admin.repository.GatewayLogReadRepository;
import org.example.admin.repository.PathStatsProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiStatsService {

    private final GatewayLogReadRepository logRepository;

    public List<ApiStats> getStats(DateRange range) {
        List<PathStatsProjection> pathStats = logRepository.findPathStats(range.getFrom(), range.getTo());

        return pathStats.stream()
                .map(ps -> buildApiStats(ps, range))
                .toList();
    }

    public List<ApiStats> getTopByCount(DateRange range, int limit) {
        return getStats(range).stream()
                .sorted(Comparator.comparingLong(ApiStats::getCount).reversed())
                .limit(limit)
                .toList();
    }

    public List<ApiStats> getTopByErrorRate(DateRange range, int limit) {
        return getStats(range).stream()
                .sorted(Comparator.comparingDouble(ApiStats::getErrorRate).reversed())
                .limit(limit)
                .toList();
    }

    public List<ApiStats> getTopByP99(DateRange range, int limit) {
        return getStats(range).stream()
                .filter(s -> s.getP99() != null)
                .sorted(Comparator.comparingLong(ApiStats::getP99).reversed())
                .limit(limit)
                .toList();
    }

    private ApiStats buildApiStats(PathStatsProjection ps, DateRange range) {
        List<Long> durations = logRepository.findDurationsByPath(ps.getPath(), range.getFrom(), range.getTo());

        Long avg = durations.isEmpty() ? null : Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0));
        Long min = durations.isEmpty() ? null : durations.getFirst();
        Long max = durations.isEmpty() ? null : durations.getLast();

        return ApiStats.builder()
                .path(ps.getPath())
                .count(ps.getCount())
                .errorCount(ps.getErrorCount())
                .errorRate(ApiStats.calculateErrorRate(ps.getErrorCount(), ps.getCount()))
                .avg(avg)
                .min(min)
                .max(max)
                .p30(PercentileCalculator.p30(durations))
                .p50(PercentileCalculator.p50(durations))
                .p75(PercentileCalculator.p75(durations))
                .p90(PercentileCalculator.p90(durations))
                .p95(PercentileCalculator.p95(durations))
                .p99(PercentileCalculator.p99(durations))
                .build();
    }
}
