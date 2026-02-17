package org.example.logbatch.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetadataKafkaListener - Kafka 배치 리스너")
class MetadataKafkaListenerTest {

    @Mock
    private MetadataBatchProcessor metadataBatchProcessor;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private MetadataKafkaListener listener;

    @Test
    @DisplayName("배치 처리 성공 시 오프셋 커밋 (ack.acknowledge)")
    void onMessage_success_acknowledges() {
        List<String> messages = List.of("{\"txId\":\"tx-1\",\"hop\":1}");
        when(metadataBatchProcessor.processBatch(messages)).thenReturn(true);

        listener.onMessage(messages, acknowledgment);

        verify(metadataBatchProcessor).processBatch(messages);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("배치 처리 실패 시 오프셋 커밋 안 함 (재처리 보장)")
    void onMessage_failure_doesNotAcknowledge() {
        List<String> messages = List.of("{\"txId\":\"tx-1\",\"hop\":1}");
        when(metadataBatchProcessor.processBatch(messages)).thenReturn(false);

        listener.onMessage(messages, acknowledgment);

        verify(metadataBatchProcessor).processBatch(messages);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("빈 배치 수신 시에도 프로세서에 위임")
    void onMessage_emptyBatch_delegatesToProcessor() {
        List<String> messages = List.of();
        when(metadataBatchProcessor.processBatch(messages)).thenReturn(true);

        listener.onMessage(messages, acknowledgment);

        verify(metadataBatchProcessor).processBatch(messages);
        verify(acknowledgment).acknowledge();
    }
}
