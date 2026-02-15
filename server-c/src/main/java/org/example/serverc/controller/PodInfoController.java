package org.example.serverc.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PodInfoController {

    private static final Logger log = LoggerFactory.getLogger(PodInfoController.class);

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of("message", "Hello from Server C");
    }

    @GetMapping("/pod-info")
    public Map<String, String> podInfo(@RequestHeader HttpHeaders headers) {
        String txId = headers.getFirst("X-Tx-Id");
        logHeaders("pod-info", headers);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("txId", txId != null ? txId : "unknown");
        result.put("message", "Hello from Server C");
        result.put("podName", env("POD_NAME"));
        result.put("podIp", env("POD_IP"));
        result.put("namespace", env("POD_NAMESPACE"));
        result.put("nodeName", env("NODE_NAME"));
        return result;
    }

    /**
     * 체인의 마지막 — 자기 정보만 반환
     */
    @GetMapping("/chain")
    public Map<String, String> chain(@RequestHeader HttpHeaders headers) {
        String txId = headers.getFirst("X-Tx-Id");
        logHeaders("chain", headers);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("txId", txId != null ? txId : "unknown");
        result.put("message", "Hello from Server C");
        result.put("podName", env("POD_NAME"));
        result.put("podIp", env("POD_IP"));
        result.put("namespace", env("POD_NAMESPACE"));
        result.put("nodeName", env("NODE_NAME"));
        return result;
    }

    private void logHeaders(String endpoint, HttpHeaders headers) {
        log.info("=== [Server-C] /{} Request Headers ===", endpoint);
        headers.forEach((name, values) ->
                log.info("  {} : {}", name, String.join(", ", values)));
    }

    private String env(String key) {
        return System.getenv().getOrDefault(key, "unknown");
    }
}
