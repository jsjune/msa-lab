package org.example.admin.controller;

import lombok.RequiredArgsConstructor;
import org.example.admin.domain.ApiStats;
import org.example.admin.domain.DateRange;
import org.example.admin.service.ApiStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class ApiStatsController {

    private final ApiStatsService apiStatsService;

    @GetMapping
    public List<ApiStats> getStats(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String path) {
        DateRange range = DateRange.of(from, to);
        List<ApiStats> stats = apiStatsService.getStats(range);

        if (path != null && !path.isBlank()) {
            return stats.stream()
                    .filter(s -> s.getPath().equals(path))
                    .toList();
        }
        return stats;
    }

    @GetMapping("/top")
    public List<ApiStats> getTop(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "count") String by,
            @RequestParam(defaultValue = "10") int limit) {
        DateRange range = DateRange.of(from, to);

        return switch (by.toLowerCase()) {
            case "errorrate" -> apiStatsService.getTopByErrorRate(range, limit);
            case "p99" -> apiStatsService.getTopByP99(range, limit);
            default -> apiStatsService.getTopByCount(range, limit);
        };
    }
}
