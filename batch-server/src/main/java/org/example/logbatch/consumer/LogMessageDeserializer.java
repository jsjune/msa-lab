package org.example.logbatch.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogMessageDeserializer {

    private final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public Map<String, Object> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Failed to deserialize Kafka message: {}", e.getMessage());
            return null;
        }
    }

    public List<Map<String, Object>> deserializeBatch(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        return messages.stream()
                .map(this::deserialize)
                .filter(Objects::nonNull)
                .toList();
    }
}
