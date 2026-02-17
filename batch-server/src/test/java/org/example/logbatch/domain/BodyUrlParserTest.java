package org.example.logbatch.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BodyUrlParser - s3:// URL 파싱")
class BodyUrlParserTest {

    @Test
    @DisplayName("정상 s3://bucket/path/txId-hop1 → bucket과 objectPrefix 분리")
    void parseValidS3Url() {
        BodyUrlParser.Result result = BodyUrlParser.parse("s3://gateway-logs/2026/02/17/abc-123-def-hop1");

        assertThat(result).isNotNull();
        assertThat(result.getBucket()).isEqualTo("gateway-logs");
        assertThat(result.getObjectPrefix()).isEqualTo("2026/02/17/abc-123-def-hop1");
    }

    @Test
    @DisplayName("objectPrefix에서 4개 오브젝트 키(.req, .res, .req.header, .res.header) 생성 확인")
    void generateFourObjectKeys() {
        BodyUrlParser.Result result = BodyUrlParser.parse("s3://gateway-logs/2026/02/17/abc-123-def-hop1");

        assertThat(result).isNotNull();
        assertThat(result.getObjectKeys()).containsExactlyInAnyOrder(
                "2026/02/17/abc-123-def-hop1.req",
                "2026/02/17/abc-123-def-hop1.res",
                "2026/02/17/abc-123-def-hop1.req.header",
                "2026/02/17/abc-123-def-hop1.res.header"
        );
    }

    @Test
    @DisplayName("s3:// prefix 없는 URL도 처리 가능")
    void parseUrlWithoutS3Prefix() {
        BodyUrlParser.Result result = BodyUrlParser.parse("gateway-logs/2026/02/17/abc-123-def-hop1");

        assertThat(result).isNotNull();
        assertThat(result.getBucket()).isEqualTo("gateway-logs");
        assertThat(result.getObjectPrefix()).isEqualTo("2026/02/17/abc-123-def-hop1");
    }

    @Test
    @DisplayName("null 또는 빈 문자열 → null 반환 (예외 없음)")
    void parseNullOrEmptyReturnsNull() {
        assertThat(BodyUrlParser.parse(null)).isNull();
        assertThat(BodyUrlParser.parse("")).isNull();
        assertThat(BodyUrlParser.parse("  ")).isNull();
    }

    @Test
    @DisplayName("/ 없는 잘못된 URL → null 반환")
    void parseInvalidUrlWithoutSlashReturnsNull() {
        assertThat(BodyUrlParser.parse("noslash")).isNull();
        assertThat(BodyUrlParser.parse("s3://")).isNull();
        assertThat(BodyUrlParser.parse("s3://bucket-only")).isNull();
    }
}
