package org.example.logbatch.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogEntryMapper {

    private LogEntryMapper() {}

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)ms");

    public static GatewayLog fromKafkaJson(Map<String, Object> json) {
        if (json == null) {
            return null;
        }

        String txId = getStringValue(json, "txId");
        if (txId == null || txId.isBlank()) {
            return null;
        }

        Instant reqTime = parseTimestamp(getStringValue(json, "reqTime"));
        Instant resTime = parseTimestamp(getStringValue(json, "resTime"));
        int partitionDay = extractPartitionDay(reqTime);

        return GatewayLog.builder()
                .txId(txId)
                .hop(getIntValue(json, "hop"))
                .path(getStringValue(json, "path"))
                .target(getStringValue(json, "target"))
                .durationMs(parseDuration(getStringValue(json, "duration")))
                .status(getIntValue(json, "status"))
                .reqTime(reqTime)
                .resTime(resTime)
                .bodyUrl(getStringValue(json, "bodyUrl"))
                .error(getStringValue(json, "error"))
                .partitionDay(partitionDay)
                .build();
    }

    private static String getStringValue(Map<String, Object> json, String key) {
        Object value = json.get(key);
        return value != null ? value.toString() : null;
    }

    private static int getIntValue(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public static GatewayLogBody toGatewayLogBody(String requestBody, String responseBody,
                                                    String requestHeaders, String responseHeaders) {
        if (isAllNull(requestBody, responseBody, requestHeaders, responseHeaders)) {
            return null;
        }

        return GatewayLogBody.builder()
                .requestBody(requestBody)
                .responseBody(responseBody)
                .requestHeaders(requestHeaders)
                .responseHeaders(responseHeaders)
                .build();
    }

    public static boolean isAllNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return false;
            }
        }
        return true;
    }

    static Long parseDuration(String duration) {
        if (duration == null || duration.isBlank()) {
            return null;
        }
        Matcher matcher = DURATION_PATTERN.matcher(duration);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return null;
    }

    static Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            return null;
        }
    }

    static int extractPartitionDay(Instant instant) {
        if (instant == null) {
            return LocalDate.now(ZoneId.of("UTC")).getDayOfMonth();
        }
        return instant.atZone(ZoneId.of("UTC")).getDayOfMonth();
    }
}