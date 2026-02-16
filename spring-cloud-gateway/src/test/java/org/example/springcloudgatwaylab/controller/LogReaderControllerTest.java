package org.example.springcloudgatwaylab.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogReaderController - bodyUrl 파싱 및 JSON 파싱")
class LogReaderControllerTest {

    @Test
    @DisplayName("정상 s3:// URL에서 bucket과 objectPrefix를 분리한다")
    void parseBodyUrl_validUrl_returnsBucketAndPrefix() {
        // given
        String bodyUrl = "s3://gateway-logs/2026/02/15/tx-abc-hop1";

        // when
        LogReaderController.BodyUrlParts parts = LogReaderController.parseBodyUrl(bodyUrl);

        // then
        assertThat(parts).isNotNull();
        assertThat(parts.bucket()).isEqualTo("gateway-logs");
        assertThat(parts.objectPrefix()).isEqualTo("2026/02/15/tx-abc-hop1");
    }

    @Test
    @DisplayName("s3:// 스킴 없는 URL도 정상 파싱한다")
    void parseBodyUrl_withoutS3Scheme_stillParses() {
        // given
        String bodyUrl = "gateway-logs/2026/02/15/tx-abc-hop1";

        // when
        LogReaderController.BodyUrlParts parts = LogReaderController.parseBodyUrl(bodyUrl);

        // then
        assertThat(parts).isNotNull();
        assertThat(parts.bucket()).isEqualTo("gateway-logs");
    }

    @Test
    @DisplayName("슬래시 없는 잘못된 URL은 null을 반환한다")
    void parseBodyUrl_noSlash_returnsNull() {
        // given
        String bodyUrl = "invalid-url-no-slash";

        // when
        LogReaderController.BodyUrlParts parts = LogReaderController.parseBodyUrl(bodyUrl);

        // then
        assertThat(parts).isNull();
    }

    @Test
    @DisplayName("빈 문자열은 null을 반환한다")
    void parseBodyUrl_emptyString_returnsNull() {
        assertThat(LogReaderController.parseBodyUrl("")).isNull();
    }

    @Test
    @DisplayName("유효한 JSON 문자열을 Object로 파싱한다")
    void parseJson_validJson_returnsParsedObject() {
        Object result = LogReaderController.parseJson("{\"key\":\"value\"}");
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(java.util.Map.class);
    }

    @Test
    @DisplayName("유효하지 않은 JSON은 raw string 그대로 반환한다")
    void parseJson_invalidJson_returnsRawString() {
        Object result = LogReaderController.parseJson("not-json");
        assertThat(result).isEqualTo("not-json");
    }

    @Test
    @DisplayName("null 또는 blank 입력은 null을 반환한다")
    void parseJson_nullOrBlank_returnsNull() {
        assertThat(LogReaderController.parseJson(null)).isNull();
        assertThat(LogReaderController.parseJson("")).isNull();
        assertThat(LogReaderController.parseJson("   ")).isNull();
    }
}