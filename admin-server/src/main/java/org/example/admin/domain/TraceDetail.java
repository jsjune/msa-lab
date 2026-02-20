package org.example.admin.domain;

import lombok.Getter;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Getter
public class TraceDetail {

    private final String txId;
    private final List<TraceHop> hops;
    private final long totalDuration;
    private final int hopCount;

    public TraceDetail(String txId, List<TraceHop> hops) {
        this.txId = txId;
        this.hops = hops == null ? List.of() : hops.stream().sorted().toList();
        this.hopCount = this.hops.size();
        this.totalDuration = calculateTotalDuration(this.hops);
    }

    private static long calculateTotalDuration(List<TraceHop> sortedHops) {
        if (sortedHops.isEmpty()) {
            return 0;
        }
        var first = sortedHops.getFirst();
        var last = sortedHops.getLast();
        if (first.getReqTime() == null || last.getResTime() == null) {
            return 0;
        }
        return Duration.between(first.getReqTime(), last.getResTime()).toMillis();
    }
}