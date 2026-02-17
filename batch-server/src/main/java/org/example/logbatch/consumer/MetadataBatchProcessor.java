package org.example.logbatch.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.logbatch.domain.GatewayLog;
import org.example.logbatch.domain.LogEntryMapper;
import org.example.logbatch.repository.GatewayLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataBatchProcessor {

    private final LogMessageDeserializer messageDeserializer;
    private final GatewayLogRepository gatewayLogRepository;

    /**
     * Kafka 메시지를 GatewayLog로 변환하여 DB에 저장한다.
     * MetadataKafkaListener에서 호출되며, @KafkaListener가 순차 호출을 보장한다.
     *
     * @return true if processing succeeded (offset should be committed), false otherwise
     */
    public boolean processBatch(List<String> rawMessages) {
        try {
            // 1. Deserialize
            List<Map<String, Object>> maps = messageDeserializer.deserializeBatch(rawMessages);
            if (maps.isEmpty()) {
                return true;
            }

            // 2. Convert to GatewayLog entities
            List<GatewayLog> logs = maps.stream()
                    .map(LogEntryMapper::fromKafkaJson)
                    .filter(Objects::nonNull)
                    .toList();

            if (logs.isEmpty()) {
                return true;
            }

            // 3. Save metadata to DB (individually, skip duplicates)
            List<GatewayLog> savedLogs = saveLogsIgnoringDuplicates(logs);
            log.info("Metadata batch processed: total={}, saved={}, duplicates={}",
                    logs.size(), savedLogs.size(), logs.size() - savedLogs.size());

            return true;
        } catch (Exception e) {
            log.error("Metadata batch processing failed", e);
            return false;
        }
    }

    private List<GatewayLog> saveLogsIgnoringDuplicates(List<GatewayLog> logs) {
        List<GatewayLog> saved = new ArrayList<>();
        for (GatewayLog logEntry : logs) {
            try {
                saved.add(gatewayLogRepository.save(logEntry));
            } catch (DataIntegrityViolationException e) {
                log.debug("Duplicate entry ignored: txId={}, hop={}",
                        logEntry.getTxId(), logEntry.getHop());
            }
        }
        return saved;
    }
}
