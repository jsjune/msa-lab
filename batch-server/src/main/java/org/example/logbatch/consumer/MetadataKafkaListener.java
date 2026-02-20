package org.example.logbatch.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "batch.metadata", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MetadataKafkaListener {

    private final MetadataBatchProcessor metadataBatchProcessor;

    @KafkaListener(
            topics = "${batch.metadata.topic:gateway-meta-logs}",
            batch = "true",
            concurrency = "3"
    )
    public void onMessage(List<String> messages, Acknowledgment ack) {
        log.debug("Received {} messages from Kafka", messages.size());

        boolean success = metadataBatchProcessor.processBatch(messages);

        if (success) {
            ack.acknowledge();
            log.debug("Offset committed for {} messages", messages.size());
        } else {
            log.warn("Batch processing failed, offset not committed. {} messages will be redelivered",
                    messages.size());
        }
    }
}
