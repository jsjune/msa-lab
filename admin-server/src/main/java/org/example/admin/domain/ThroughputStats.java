package org.example.admin.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThroughputStats {

    private long totalRequests;
    private double avgPerMinute;
    private long maxPerMinute;
}
