package org.example.logbatch.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayLogBody {

    private Long id;
    private GatewayLog gatewayLog;
    private String requestBody;
    private String responseBody;
    private String requestHeaders;
    private String responseHeaders;
}
