package org.example.springcloudgatwaylab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class KafkaMetadataSender {

    private static final Logger logger = LoggerFactory.getLogger(KafkaMetadataSender.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaMetadataSender(KafkaTemplate<String, String> kafkaTemplate,
                               @Value("${gateway.kafka.topic.metadata:gateway-meta-logs}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void send(Map<String, Object> metadata) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(metadata);
            kafkaTemplate.send(topic, (String) metadata.get("txId"), jsonMessage)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            logger.error("Failed to send log to Kafka: {}", ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            logger.error("Error serializing log message", e);
        }
    }
}
