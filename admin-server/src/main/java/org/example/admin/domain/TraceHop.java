package org.example.admin.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceHop implements Comparable<TraceHop> {

    private String txId;
    private int hop;
    private String path;
    private String target;
    private int status;
    private Long durationMs;
    private Instant reqTime;
    private Instant resTime;
    private String error;

    @Override
    public int compareTo(TraceHop other) {
        return Integer.compare(this.hop, other.hop);
    }
}