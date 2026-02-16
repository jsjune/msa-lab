package org.example.logbatch.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayLog {

    private Long id;
    private String txId;
    private int hop;
    private String path;
    private String target;
    private Long durationMs;
    private int status;
    private Instant reqTime;
    private Instant resTime;
    private String bodyUrl;
    private String error;
    private int partitionDay;
}
