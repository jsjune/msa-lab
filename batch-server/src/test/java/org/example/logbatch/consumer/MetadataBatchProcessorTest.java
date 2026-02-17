package org.example.logbatch.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.logbatch.domain.GatewayLog;
import org.example.logbatch.repository.GatewayLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetadataBatchProcessor - 메타데이터 배치 비즈니스 로직")
class MetadataBatchProcessorTest {

    @Mock
    private GatewayLogRepository gatewayLogRepository;

    private MetadataBatchProcessor processor;

    private static final String VALID_JSON = """
            {"txId":"abc-123","hop":1,"path":"/server-a/hello","target":"http://localhost:8081/hello","duration":"45ms","status":200,"reqTime":"2026-02-17T01:23:45.678Z","resTime":"2026-02-17T01:23:45.723Z","bodyUrl":"s3://gateway-logs/2026/02/17/abc-123-hop1","error":null}
            """;

    @BeforeEach
    void setUp() {
        LogMessageDeserializer deserializer = new LogMessageDeserializer(new ObjectMapper());
        processor = new MetadataBatchProcessor(deserializer, gatewayLogRepository);
    }

    // ── 메타데이터 저장 ──

    @Test
    @DisplayName("Kafka에서 N건 poll → GatewayLog 변환 → DB 저장 순서 검증")
    void processBatch_validMessages_savesToDb() {
        String json2 = """
                {"txId":"def-456","hop":1,"path":"/server-b/data","status":201,"reqTime":"2026-02-17T02:00:00Z","resTime":"2026-02-17T02:00:00.050Z","bodyUrl":"s3://gateway-logs/2026/02/17/def-456-hop1"}
                """;

        when(gatewayLogRepository.save(any(GatewayLog.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = processor.processBatch(List.of(VALID_JSON, json2));

        assertThat(result).isTrue();
        verify(gatewayLogRepository, times(2)).save(any(GatewayLog.class));
    }

    @Test
    @DisplayName("변환 단계 실패 건 → 스킵 후 나머지 정상 저장")
    void processBatch_partialConversionFailure_savesSuccessful() {
        String invalidJson = "{invalid}";

        when(gatewayLogRepository.save(any(GatewayLog.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = processor.processBatch(List.of(VALID_JSON, invalidJson));

        assertThat(result).isTrue();
        verify(gatewayLogRepository, times(1)).save(any(GatewayLog.class));
    }

    @Test
    @DisplayName("DB 저장 실패 (모든 건) → false 반환 (오프셋 커밋 안 함)")
    void processBatch_dbFailure_returnsFalse() {
        when(gatewayLogRepository.save(any(GatewayLog.class)))
                .thenThrow(new RuntimeException("DB connection error"));

        boolean result = processor.processBatch(List.of(VALID_JSON));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("빈 배치 → true 반환, DB 호출 없음")
    void processBatch_empty_returnsTrue() {
        boolean result = processor.processBatch(Collections.emptyList());

        assertThat(result).isTrue();
        verify(gatewayLogRepository, never()).save(any(GatewayLog.class));
    }

    // ── 중복 처리 (Idempotency) ──

    @Test
    @DisplayName("동일 txId+hop 메시지 재수신 → 중복 무시, 나머지 정상 저장")
    void processBatch_duplicateMessage_ignoredGracefully() {
        when(gatewayLogRepository.save(any(GatewayLog.class)))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        boolean result = processor.processBatch(List.of(VALID_JSON, VALID_JSON));

        assertThat(result).isTrue();
        verify(gatewayLogRepository, times(2)).save(any(GatewayLog.class));
    }

    @Test
    @DisplayName("Kafka 리밸런싱 후 재처리 → 중복 건 무시, 새 건만 저장")
    void processBatch_rebalanceReprocess_duplicatesIgnored() {
        String json2 = """
                {"txId":"new-tx","hop":1,"path":"/server-b/data","status":200,"reqTime":"2026-02-17T02:00:00Z","resTime":"2026-02-17T02:00:00.050Z","bodyUrl":"s3://gateway-logs/2026/02/17/new-tx-hop1"}
                """;

        when(gatewayLogRepository.save(any(GatewayLog.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate"))
                .thenAnswer(inv -> inv.getArgument(0));

        boolean result = processor.processBatch(List.of(VALID_JSON, json2));

        assertThat(result).isTrue();
        verify(gatewayLogRepository, times(2)).save(any(GatewayLog.class));
    }
}
