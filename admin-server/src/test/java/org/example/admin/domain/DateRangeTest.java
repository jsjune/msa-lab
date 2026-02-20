package org.example.admin.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    @Test
    @DisplayName("필드 보유 확인 — from, to 접근 가능")
    void fieldsAccessible() {
        Instant from = Instant.parse("2026-02-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-02T00:00:00Z");

        DateRange range = DateRange.of(from, to);

        assertThat(range.getFrom()).isEqualTo(from);
        assertThat(range.getTo()).isEqualTo(to);
    }

    @Test
    @DisplayName("기본값 팩토리 — lastHours(24)는 최근 24시간 범위")
    void lastHours_creates24hRange() {
        DateRange range = DateRange.lastHours(24);

        Duration duration = Duration.between(range.getFrom(), range.getTo());
        assertThat(duration).isCloseTo(Duration.ofHours(24), Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("from만 지정 → to = 현재 시각")
    void fromOnly_toIsNow() {
        Instant from = Instant.now().minus(Duration.ofHours(6));

        DateRange range = DateRange.of(from, null);

        assertThat(range.getFrom()).isEqualTo(from);
        assertThat(range.getTo()).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("to만 지정 → from = to - 24시간")
    void toOnly_fromIsTo24hAgo() {
        Instant to = Instant.parse("2026-02-20T12:00:00Z");

        DateRange range = DateRange.of(null, to);

        assertThat(range.getTo()).isEqualTo(to);
        assertThat(range.getFrom()).isEqualTo(to.minus(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("from > to → IllegalArgumentException")
    void fromAfterTo_throws() {
        Instant from = Instant.parse("2026-02-20T12:00:00Z");
        Instant to = Instant.parse("2026-02-20T06:00:00Z");

        assertThatThrownBy(() -> DateRange.of(from, to))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("최대 30일 초과 → IllegalArgumentException")
    void exceedsMaxDuration_throws() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-20T00:00:00Z");

        assertThatThrownBy(() -> DateRange.of(from, to))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("둘 다 null → 최근 24시간 기본값")
    void bothNull_defaultLast24h() {
        DateRange range = DateRange.of(null, null);

        Duration duration = Duration.between(range.getFrom(), range.getTo());
        assertThat(duration).isCloseTo(Duration.ofHours(24), Duration.ofSeconds(1));
    }
}