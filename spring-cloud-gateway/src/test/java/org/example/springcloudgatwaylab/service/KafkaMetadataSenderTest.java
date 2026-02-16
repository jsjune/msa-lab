package org.example.springcloudgatwaylab.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("KafkaMetadataSender - 메타데이터 Kafka 전송")
class KafkaMetadataSenderTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private KafkaMetadataSender sender;

    @BeforeEach
    void setUp() {
        sender = new KafkaMetadataSender(kafkaTemplate, "test-topic");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(new CompletableFuture<>());
    }

    @Test
    @DisplayName("정상 metadata를 JSON 직렬화 후 kafkaTemplate.send()를 호출한다")
    void send_validMetadata_callsKafkaTemplateSend() {
        // given
        Map<String, Object> metadata = Map.of(
                "txId", "tx-123",
                "hop", 1,
                "path", "/server-a/hello"
        );

        // when
        sender.send(metadata);

        // then
        verify(kafkaTemplate).send(eq("test-topic"), eq("tx-123"), argThat(json ->
                json.contains("\"txId\"") && json.contains("tx-123")
        ));
    }

    @Test
    @DisplayName("Kafka send 비동기 실패 시 예외를 전파하지 않는다")
    void send_asyncFailure_doesNotPropagate() {
        // given
        CompletableFuture failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);

        Map<String, Object> metadata = Map.of("txId", "tx-fail", "hop", 1);

        // when & then — no exception thrown
        sender.send(metadata);
    }

    @Test
    @DisplayName("null metadata 전송 시 예외를 전파하지 않는다")
    void send_nullMetadata_doesNotPropagate() {
        // when & then — no exception thrown
        sender.send(null);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }
}
