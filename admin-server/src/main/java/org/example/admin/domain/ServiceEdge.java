package org.example.admin.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceEdge {

    private String source;
    private String target;
    private long requestCount;
    private double errorRate;
    private Long p50;
    private Long p99;
}
