package org.example.admin.domain;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;

@Getter
public class DateRange {

    private static final Duration DEFAULT_DURATION = Duration.ofHours(24);
    private static final Duration MAX_DURATION = Duration.ofDays(30);

    private final Instant from;
    private final Instant to;

    private DateRange(Instant from, Instant to) {
        this.from = from;
        this.to = to;
    }

    public static DateRange of(Instant from, Instant to) {
        Instant now = Instant.now();

        if (from == null && to == null) {
            return new DateRange(now.minus(DEFAULT_DURATION), now);
        }
        if (from != null && to == null) {
            return validated(from, now);
        }
        if (from == null) {
            return validated(to.minus(DEFAULT_DURATION), to);
        }
        return validated(from, to);
    }

    public static DateRange lastHours(int hours) {
        Instant now = Instant.now();
        return new DateRange(now.minus(Duration.ofHours(hours)), now);
    }

    private static DateRange validated(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
        if (Duration.between(from, to).compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException("Date range must not exceed 30 days");
        }
        return new DateRange(from, to);
    }
}