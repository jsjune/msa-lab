package org.example.admin.service;

import lombok.RequiredArgsConstructor;
import org.example.admin.domain.ApiStats;
import org.example.admin.domain.DateRange;
import org.example.admin.domain.PercentileCalculator;
import org.example.admin.domain.ServiceEdge;
import org.example.admin.domain.ServiceNode;
import org.example.admin.domain.ThroughputStats;
import org.example.admin.domain.TrafficGraph;
import org.example.admin.repository.GatewayLogReadRepository;
import org.example.admin.repository.HopRawProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceGraphService {

    private final GatewayLogReadRepository logRepository;

    public TrafficGraph buildGraph(DateRange range) {
        List<HopRawProjection> hops = logRepository.findHopRawData(range.getFrom(), range.getTo());

        if (hops.isEmpty()) {
            return TrafficGraph.builder().nodes(List.of()).edges(List.of()).build();
        }

        Map<String, List<HopRawProjection>> byTxId = hops.stream()
                .collect(Collectors.groupingBy(HopRawProjection::getTxId));

        // (source, target) → 해당 엣지를 통과한 hop 목록
        Map<String, Map<String, List<HopRawProjection>>> edgeData = new LinkedHashMap<>();

        for (List<HopRawProjection> txHops : byTxId.values()) {
            List<HopRawProjection> sorted = txHops.stream()
                    .sorted(Comparator.comparingInt(HopRawProjection::getHop))
                    .toList();

            String prevTarget = "external";
            for (HopRawProjection hop : sorted) {
                String source = hop.getHop() == 1 ? "external" : prevTarget;
                String target = extractService(hop.getPath());

                edgeData.computeIfAbsent(source, k -> new LinkedHashMap<>())
                        .computeIfAbsent(target, k -> new ArrayList<>())
                        .add(hop);

                prevTarget = target;
            }
        }

        // 노드별 inbound hop 목록
        Map<String, List<HopRawProjection>> inboundByNode = new LinkedHashMap<>();
        List<ServiceEdge> edges = new ArrayList<>();

        for (Map.Entry<String, Map<String, List<HopRawProjection>>> srcEntry : edgeData.entrySet()) {
            String source = srcEntry.getKey();
            for (Map.Entry<String, List<HopRawProjection>> tgtEntry : srcEntry.getValue().entrySet()) {
                String target = tgtEntry.getKey();
                List<HopRawProjection> edgeHops = tgtEntry.getValue();

                long requestCount = edgeHops.size();
                long errorCount = edgeHops.stream().filter(h -> h.getStatus() >= 400).count();
                List<Long> durations = edgeHops.stream().map(HopRawProjection::getDurationMs).toList();

                edges.add(ServiceEdge.builder()
                        .source(source)
                        .target(target)
                        .requestCount(requestCount)
                        .errorRate(ApiStats.calculateErrorRate(errorCount, requestCount))
                        .p50(PercentileCalculator.p50(durations))
                        .p99(PercentileCalculator.p99(durations))
                        .build());

                inboundByNode.computeIfAbsent(target, k -> new ArrayList<>()).addAll(edgeHops);
            }
        }

        Set<String> allServices = new LinkedHashSet<>();
        allServices.add("external");
        edges.forEach(e -> { allServices.add(e.getSource()); allServices.add(e.getTarget()); });

        List<ServiceNode> nodes = allServices.stream()
                .map(name -> {
                    List<HopRawProjection> inbound = inboundByNode.getOrDefault(name, List.of());
                    long requestCount = inbound.size();
                    long errorCount = inbound.stream().filter(h -> h.getStatus() >= 400).count();
                    Long avgDuration = inbound.isEmpty() ? null
                            : Math.round(inbound.stream().mapToLong(HopRawProjection::getDurationMs).average().orElse(0));

                    return ServiceNode.builder()
                            .name(name)
                            .requestCount(requestCount)
                            .errorRate(ApiStats.calculateErrorRate(errorCount, requestCount))
                            .avgDuration(avgDuration)
                            .build();
                })
                .toList();

        return TrafficGraph.builder().nodes(nodes).edges(edges).build();
    }

    public ThroughputStats calcThroughput(DateRange range) {
        List<Instant> times = logRepository.findExternalRequestTimes(range.getFrom(), range.getTo());
        long total = times.size();

        double durationMinutes = Duration.between(range.getFrom(), range.getTo()).toSeconds() / 60.0;
        double avgPerMinute = durationMinutes > 0 ? total / durationMinutes : 0;

        Map<Long, Long> perMinute = times.stream()
                .collect(Collectors.groupingBy(t -> t.getEpochSecond() / 60, Collectors.counting()));
        long maxPerMinute = perMinute.values().stream().mapToLong(Long::longValue).max().orElse(0);

        return ThroughputStats.builder()
                .totalRequests(total)
                .avgPerMinute(Math.round(avgPerMinute * 10.0) / 10.0)
                .maxPerMinute(maxPerMinute)
                .build();
    }

    private String extractService(String path) {
        if (path == null || path.isBlank()) return "unknown";
        String[] parts = path.split("/");
        return parts.length > 1 ? parts[1] : "unknown";
    }
}
