package org.example.admin.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathPatternValidatorTest {

    @Test
    @DisplayName("와일드카드 패턴 — /server-a/** 형식은 유효")
    void wildcardPattern_valid() {
        assertThat(PathPatternValidator.isValid("/server-a/**")).isTrue();
    }

    @Test
    @DisplayName("정확한 경로 — /server-a/data는 유효")
    void exactPath_valid() {
        assertThat(PathPatternValidator.isValid("/server-a/data")).isTrue();
    }

    @Test
    @DisplayName("빈 문자열 — 유효하지 않음")
    void emptyString_invalid() {
        assertThat(PathPatternValidator.isValid("")).isFalse();
        assertThat(PathPatternValidator.isValid("  ")).isFalse();
        assertThat(PathPatternValidator.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("/로 시작하지 않는 패턴 — 유효하지 않음")
    void noLeadingSlash_invalid() {
        assertThat(PathPatternValidator.isValid("server-a/**")).isFalse();
    }
}