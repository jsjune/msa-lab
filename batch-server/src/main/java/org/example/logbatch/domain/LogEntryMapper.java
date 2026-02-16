package org.example.logbatch.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogEntryMapper {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)ms");

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