package org.example.logbatch.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogMessageDeserializer - Kafka 메시지 역직렬화")
class LogMessageDeserializerTest {

    private final LogMessageDeserializer deserializer = new LogMessageDeserializer(new ObjectMapper());

    // ── 5.1 단건 메시지 역직렬화 ──

    @Test
    @DisplayName("정상 JSON 메시지 → Map 변환 성공")
    void deserialize_validJson_returnsMap() {
        String json = """
                {"txId":"abc-123","hop":1,"path":"/server-a/hello","status":200}
                """;

        Map<String, Object> result = deserializer.deserialize(json);

        assertThat(result).isNotNull();
        assertThat(result.get("txId")).isEqualTo("abc-123");
        assertThat(result.get("hop")).isEqualTo(1);
        assertThat(result.get("path")).isEqualTo("/server-a/hello");
        assertThat(result.get("status")).isEqualTo(200);
    }

    @Test
    @DisplayName("잘못된 JSON 메시지 → null 반환, 로그 경고 (poison pill 방지)")
    void deserialize_invalidJson_returnsNull() {
        assertThat(deserializer.deserialize("{invalid json}")).isNull();
        assertThat(deserializer.deserialize("not json at all")).isNull();
    }

    @Test
    @DisplayName("빈 메시지 → null 반환 (스킵)")
    void deserialize_emptyOrNull_returnsNull() {
        assertThat(deserializer.deserialize(null)).isNull();
        assertThat(deserializer.deserialize("")).isNull();
        assertThat(deserializer.deserialize("  ")).isNull();
    }

    // ── 5.2 배치 메시지 처리 ──

    @Test
    @DisplayName("N건 배치 수신 → N건 전부 Map으로 변환")
    void deserializeBatch_allValid_returnsAll() {
        List<String> messages = List.of(
                "{\"txId\":\"tx-1\",\"hop\":1}",
                "{\"txId\":\"tx-2\",\"hop\":1}",
                "{\"txId\":\"tx-3\",\"hop\":1}"
        );

        List<Map<String, Object>> result = deserializer.deserializeBatch(messages);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).get("txId")).isEqualTo("tx-1");
        assertThat(result.get(1).get("txId")).isEqualTo("tx-2");
        assertThat(result.get(2).get("txId")).isEqualTo("tx-3");
    }

    @Test
    @DisplayName("배치 중 일부 역직렬화 실패 → 성공 건만 리스트에 포함")
    void deserializeBatch_partialFailure_onlySuccessIncluded() {
        List<String> messages = List.of(
                "{\"txId\":\"tx-1\",\"hop\":1}",
                "{invalid}",
                "{\"txId\":\"tx-3\",\"hop\":1}"
        );

        List<Map<String, Object>> result = deserializer.deserializeBatch(messages);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("txId")).isEqualTo("tx-1");
        assertThat(result.get(1).get("txId")).isEqualTo("tx-3");
    }

    @Test
    @DisplayName("빈 배치 (poll 결과 0건) → 빈 리스트 반환")
    void deserializeBatch_empty_returnsEmptyList() {
        assertThat(deserializer.deserializeBatch(Collections.emptyList())).isEmpty();
        assertThat(deserializer.deserializeBatch(null)).isEmpty();
    }
}
