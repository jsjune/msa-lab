package org.example.admin.domain;

public final class PathPatternValidator {

    private PathPatternValidator() {}

    public static boolean isValid(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        return pattern.startsWith("/");
    }
}