package org.example.logbatch.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

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
}