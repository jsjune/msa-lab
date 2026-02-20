package org.example.admin.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceNode {

    private String name;
    private long requestCount;
    private double errorRate;
    private Long avgDuration;
}
