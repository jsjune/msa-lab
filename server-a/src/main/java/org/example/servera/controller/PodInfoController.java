package org.example.servera.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
public class PodInfoController {

    private static final Logger log = LoggerFactory.getLogger(PodInfoController.class);

    private final WebClient webClient;

    public PodInfoController(@Value("${gateway.url}") String gatewayUrl) {
        this.webClient = WebClient.builder().baseUrl(gatewayUrl).build();
    }

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of("message", "Hello from Server A");
    }

    @GetMapping("/pod-info")
    public Map<String, String> podInfo(@RequestHeader HttpHeaders headers) {
        String txId = headers.getFirst("X-Tx-Id");
        logHeaders("pod-info", headers);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("txId", txId != null ? txId : "unknown");
        result.put("message", "Hello from Server A");
        result.put("podName", env("POD_NAME"));
        result.put("podIp", env("POD_IP"));
        result.put("namespace", env("POD_NAMESPACE"));
        result.put("nodeName", env("NODE_NAME"));
        return result;
    }

    /**
     * server-a → (gateway) → server-b /chain → (gateway) → server-c /pod-info
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/chain")
    public Mono<Map<String, Object>> chain(@RequestHeader HttpHeaders headers) {
        String txId = headers.getFirst("X-Tx-Id");
        logHeaders("chain", headers);

        Map<String, Object> serverAInfo = new LinkedHashMap<>();
        serverAInfo.put("message", "Hello from Server A");
        serverAInfo.put("podName", env("POD_NAME"));
        serverAInfo.put("podIp", env("POD_IP"));
        serverAInfo.put("namespace", env("POD_NAMESPACE"));
        serverAInfo.put("nodeName", env("NODE_NAME"));

        return webClient.get()
                .uri("/server-b/chain")
                .headers(h -> {
                    h.addAll(headers);
                    h.set("X-Server-A-Sample", "passed-through-server-a");
                })
                .retrieve()
                .bodyToMono(Map.class)
                .map(serverBResponse -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("txId", txId != null ? txId : "unknown");
                    result.put("server-a", serverAInfo);
                    result.put("next", serverBResponse);
                    return result;
                });
    }

    private void logHeaders(String endpoint, HttpHeaders headers) {
        log.info("=== [Server-A] /{} Request Headers ===", endpoint);
        headers.forEach((name, values) ->
                log.info("  {} : {}", name, String.join(", ", values)));
    }

    private String env(String key) {
        return System.getenv().getOrDefault(key, "unknown");
    }
}
