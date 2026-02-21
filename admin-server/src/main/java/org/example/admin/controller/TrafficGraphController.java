package org.example.admin.controller;

import lombok.RequiredArgsConstructor;
import org.example.admin.domain.DateRange;
import org.example.admin.domain.ThroughputStats;
import org.example.admin.domain.TrafficGraph;
import org.example.admin.service.ServiceGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/traffic")
@RequiredArgsConstructor
public class TrafficGraphController {

    private final ServiceGraphService serviceGraphService;

    @GetMapping("/graph")
    public TrafficGraph getGraph(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        DateRange range = DateRange.of(from, to);
        return serviceGraphService.buildGraph(range);
    }

    @GetMapping("/throughput")
    public ThroughputStats getThroughput(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        DateRange range = DateRange.of(from, to);
        return serviceGraphService.calcThroughput(range);
    }
}
