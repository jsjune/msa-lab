package org.example.logbatch.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogEntryMapper - 값 변환 로직")
class LogEntryMapperTest {

    // ── 1.3 Duration 파싱 ──

    @Test
    @DisplayName("\"45ms\" → 45L로 변환한다")
    void parseDuration_normal_returnsLong() {
        assertThat(LogEntryMapper.parseDuration("45ms")).isEqualTo(45L);
    }

    @Test
    @DisplayName("\"0ms\" → 0L로 변환한다")
    void parseDuration_zero_returnsZero() {
        assertThat(LogEntryMapper.parseDuration("0ms")).isEqualTo(0L);
    }

    @Test
    @DisplayName("null 또는 빈 문자열 → null을 반환한다")
    void parseDuration_nullOrEmpty_returnsNull() {
        assertThat(LogEntryMapper.parseDuration(null)).isNull();
        assertThat(LogEntryMapper.parseDuration("")).isNull();
        assertThat(LogEntryMapper.parseDuration("  ")).isNull();
    }

    @Test
    @DisplayName("숫자 없는 문자열 \"abc\" → null을 반환한다 (예외 없음)")
    void parseDuration_noDigits_returnsNull() {
        assertThat(LogEntryMapper.parseDuration("abc")).isNull();
        assertThat(LogEntryMapper.parseDuration("ms")).isNull();
    }

    // ── 1.4 Timestamp 파싱 ──

    @Test
    @DisplayName("정상 ISO 8601 문자열 → Instant로 변환한다")
    void parseTimestamp_validIso8601_returnsInstant() {
        Instant result = LogEntryMapper.parseTimestamp("2026-02-17T01:23:45.678Z");
        assertThat(result).isEqualTo(Instant.parse("2026-02-17T01:23:45.678Z"));
    }

    @Test
    @DisplayName("null 또는 빈 문자열 → null을 반환한다")
    void parseTimestamp_nullOrEmpty_returnsNull() {
        assertThat(LogEntryMapper.parseTimestamp(null)).isNull();
        assertThat(LogEntryMapper.parseTimestamp("")).isNull();
        assertThat(LogEntryMapper.parseTimestamp("  ")).isNull();
    }

    @Test
    @DisplayName("잘못된 포맷 → null을 반환한다 (예외 없음)")
    void parseTimestamp_invalidFormat_returnsNull() {
        assertThat(LogEntryMapper.parseTimestamp("not-a-date")).isNull();
        assertThat(LogEntryMapper.parseTimestamp("2026/02/17")).isNull();
    }

    // ── 1.5 partitionDay 추출 ──

    @Test
    @DisplayName("Instant → day-of-month (1~31)를 추출한다")
    void extractPartitionDay_validInstant_returnsDayOfMonth() {
        Instant feb17 = Instant.parse("2026-02-17T10:00:00Z");
        assertThat(LogEntryMapper.extractPartitionDay(feb17)).isEqualTo(17);
    }

    @Test
    @DisplayName("null Instant → 현재 일자를 반환한다")
    void extractPartitionDay_null_returnsCurrentDay() {
        int today = LocalDate.now(ZoneId.of("UTC")).getDayOfMonth();
        assertThat(LogEntryMapper.extractPartitionDay(null)).isEqualTo(today);
    }

    // ── 1.7 JSON → GatewayLog 변환 ──

    private Map<String, Object> validKafkaJson() {
        Map<String, Object> json = new HashMap<>();
        json.put("txId", "abc-123-def");
        json.put("hop", 1);
        json.put("path", "/server-a/hello");
        json.put("target", "http://localhost:8081/hello");
        json.put("duration", "45ms");
        json.put("status", 200);
        json.put("reqTime", "2026-02-17T01:23:45.678Z");
        json.put("resTime", "2026-02-17T01:23:45.723Z");
        json.put("bodyUrl", "s3://gateway-logs/2026/02/17/abc-123-def-hop1");
        json.put("error", null);
        return json;
    }

    @Test
    @DisplayName("정상 Kafka JSON → GatewayLog 매핑 성공 (partitionDay 자동 계산 포함)")
    void fromKafkaJson_validJson_mapsAllFields() {
        GatewayLog log = LogEntryMapper.fromKafkaJson(validKafkaJson());

        assertThat(log).isNotNull();
        assertThat(log.getTxId()).isEqualTo("abc-123-def");
        assertThat(log.getHop()).isEqualTo(1);
        assertThat(log.getPath()).isEqualTo("/server-a/hello");
        assertThat(log.getTarget()).isEqualTo("http://localhost:8081/hello");
        assertThat(log.getDurationMs()).isEqualTo(45L);
        assertThat(log.getStatus()).isEqualTo(200);
        assertThat(log.getReqTime()).isEqualTo(Instant.parse("2026-02-17T01:23:45.678Z"));
        assertThat(log.getResTime()).isEqualTo(Instant.parse("2026-02-17T01:23:45.723Z"));
        assertThat(log.getBodyUrl()).isEqualTo("s3://gateway-logs/2026/02/17/abc-123-def-hop1");
        assertThat(log.getPartitionDay()).isEqualTo(17);
    }

    @Test
    @DisplayName("error 필드 있는 JSON → error 포함된 엔티티 생성")
    void fromKafkaJson_withError_includesError() {
        Map<String, Object> json = validKafkaJson();
        json.put("error", "Connection refused");
        json.put("status", 502);

        GatewayLog log = LogEntryMapper.fromKafkaJson(json);

        assertThat(log).isNotNull();
        assertThat(log.getError()).isEqualTo("Connection refused");
        assertThat(log.getStatus()).isEqualTo(502);
    }

    @Test
    @DisplayName("error 필드 없는 JSON → error가 null인 엔티티 생성")
    void fromKafkaJson_withoutError_errorIsNull() {
        Map<String, Object> json = validKafkaJson();
        // error is already null in validKafkaJson()

        GatewayLog log = LogEntryMapper.fromKafkaJson(json);

        assertThat(log).isNotNull();
        assertThat(log.getError()).isNull();
    }

    @Test
    @DisplayName("필수 필드(txId) 누락 JSON → null 반환")
    void fromKafkaJson_missingTxId_returnsNull() {
        Map<String, Object> json = validKafkaJson();
        json.remove("txId");

        assertThat(LogEntryMapper.fromKafkaJson(json)).isNull();
        assertThat(LogEntryMapper.fromKafkaJson(null)).isNull();
    }

    // ── 1.8 MinIO 컨텐츠 → GatewayLogBody 변환 ──

    @Test
    @DisplayName("4개 문자열(req, res, reqHeader, resHeader) → GatewayLogBody 매핑 성공")
    void toGatewayLogBody_allFields_mapsSuccessfully() {
        GatewayLogBody body = LogEntryMapper.toGatewayLogBody(
                "{\"name\":\"test\"}", "{\"result\":\"ok\"}",
                "{\"Content-Type\":\"application/json\"}", "{\"Status\":\"200\"}");

        assertThat(body).isNotNull();
        assertThat(body.getRequestBody()).isEqualTo("{\"name\":\"test\"}");
        assertThat(body.getResponseBody()).isEqualTo("{\"result\":\"ok\"}");
        assertThat(body.getRequestHeaders()).isEqualTo("{\"Content-Type\":\"application/json\"}");
        assertThat(body.getResponseHeaders()).isEqualTo("{\"Status\":\"200\"}");
    }

    @Test
    @DisplayName("일부 null → 해당 필드만 null인 엔티티 생성")
    void toGatewayLogBody_partialNull_createsEntityWithNulls() {
        GatewayLogBody body = LogEntryMapper.toGatewayLogBody(
                null, "{\"result\":\"ok\"}", null, "{\"Status\":\"200\"}");

        assertThat(body).isNotNull();
        assertThat(body.getRequestBody()).isNull();
        assertThat(body.getResponseBody()).isEqualTo("{\"result\":\"ok\"}");
        assertThat(body.getRequestHeaders()).isNull();
        assertThat(body.getResponseHeaders()).isEqualTo("{\"Status\":\"200\"}");
    }

    @Test
    @DisplayName("전부 null → null 반환 (body row 생성 안 함)")
    void toGatewayLogBody_allNull_returnsNull() {
        assertThat(LogEntryMapper.toGatewayLogBody(null, null, null, null)).isNull();
    }
}