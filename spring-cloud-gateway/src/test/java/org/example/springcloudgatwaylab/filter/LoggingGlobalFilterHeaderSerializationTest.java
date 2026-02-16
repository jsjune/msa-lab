package org.example.springcloudgatwaylab.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoggingGlobalFilter - 헤더 직렬화")
class LoggingGlobalFilterHeaderSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("정상 HttpHeaders를 JSON byte[]로 직렬화한다")
    void serializeHeaders_withValidHeaders_returnsJsonBytes() throws Exception {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("X-Tx-Id", "test-tx-123");

        // when
        byte[] result = LoggingGlobalFilter.serializeHeaders(headers);

        // then
        assertThat(result).isNotEmpty();

        Map<String, String> parsed = objectMapper.readValue(result, Map.class);
        assertThat(parsed).containsEntry("Content-Type", "application/json");
        assertThat(parsed).containsEntry("X-Tx-Id", "test-tx-123");
    }

    @Test
    @DisplayName("빈 HttpHeaders는 빈 JSON 객체 byte[]를 반환한다")
    void serializeHeaders_withEmptyHeaders_returnsEmptyJsonObject() throws Exception {
        // given
        HttpHeaders headers = new HttpHeaders();

        // when
        byte[] result = LoggingGlobalFilter.serializeHeaders(headers);

        // then
        assertThat(result).isNotEmpty();

        Map<String, String> parsed = objectMapper.readValue(result, Map.class);
        assertThat(parsed).isEmpty();
    }

    @Test
    @DisplayName("다중 값 헤더는 첫 번째 값만 포함한다")
    void serializeHeaders_withMultipleValues_returnsFirstValueOnly() throws Exception {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.add("Accept", "text/html");
        headers.add("Accept", "application/json");
        headers.add("Accept", "text/plain");

        // when
        byte[] result = LoggingGlobalFilter.serializeHeaders(headers);

        // then
        Map<String, String> parsed = objectMapper.readValue(result, Map.class);
        assertThat(parsed).containsEntry("Accept", "text/html");
        assertThat(parsed).hasSize(1);
    }

    @Test
    @DisplayName("null 입력 시 예외 없이 빈 byte[]를 반환한다")
    void serializeHeaders_withNull_returnsEmptyByteArrayWithoutException() {
        // given / when
        byte[] result = LoggingGlobalFilter.serializeHeaders(null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("한글/특수문자가 포함된 헤더도 정상 직렬화된다")
    void serializeHeaders_withKoreanAndSpecialChars_serializesCorrectly() throws Exception {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Custom-Name", "홍길동");
        headers.add("X-Description", "테스트 값 @#$%^&*()");
        headers.add("X-Emoji", "✅🚀");

        // when
        byte[] result = LoggingGlobalFilter.serializeHeaders(headers);

        // then
        assertThat(result).isNotEmpty();

        Map<String, String> parsed = objectMapper.readValue(result, Map.class);
        assertThat(parsed).containsEntry("X-Custom-Name", "홍길동");
        assertThat(parsed).containsEntry("X-Description", "테스트 값 @#$%^&*()");
        assertThat(parsed).containsEntry("X-Emoji", "✅🚀");
    }
}
