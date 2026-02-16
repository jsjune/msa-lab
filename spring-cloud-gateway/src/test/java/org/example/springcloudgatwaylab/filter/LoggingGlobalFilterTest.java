package org.example.springcloudgatwaylab.filter;

import org.example.springcloudgatwaylab.service.KafkaMetadataSender;
import org.example.springcloudgatwaylab.service.LogStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.mockito.ArgumentCaptor;
import org.springframework.core.Ordered;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LoggingGlobalFilter - 필터 체인 로직")
class LoggingGlobalFilterTest {

    private LogStorageService storageService;
    private KafkaMetadataSender metadataSender;
    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;
    private GatewayFilterChain chain;
    private LoggingGlobalFilter filter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        storageService = mock(LogStorageService.class);
        metadataSender = mock(KafkaMetadataSender.class);
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        chain = mock(GatewayFilterChain.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(storageService.getStorageBaseUrl(anyString(), anyInt())).thenReturn("s3://bucket/path");

        filter = new LoggingGlobalFilter(storageService, metadataSender, redisTemplate);
    }

    @Test
    @DisplayName("X-Tx-Id 헤더가 없으면 새 UUID를 생성하고 downstream 헤더에 추가한다")
    void filter_noTxIdHeader_generatesNewUuidAndAddsToDownstream() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/server-a/hello")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // capture the mutated exchange passed to chain
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange mutated = invocation.getArgument(0);
            String txId = mutated.getRequest().getHeaders().getFirst("X-Tx-Id");
            assertThat(txId).isNotNull();
            assertThat(txId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            return Mono.empty();
        });

        // when & then
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("X-Tx-Id 헤더가 있으면 기존 ID를 그대로 사용한다")
    void filter_withTxIdHeader_usesExistingId() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/server-a/hello")
                .header("X-Tx-Id", "existing-tx-id")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange mutated = invocation.getArgument(0);
            String txId = mutated.getRequest().getHeaders().getFirst("X-Tx-Id");
            assertThat(txId).isEqualTo("existing-tx-id");
            return Mono.empty();
        });

        // when & then
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // existing txId → Redis key should NOT be deleted (isNewTx = false)
        verify(redisTemplate, never()).delete(anyString());
    }

    // ── 3.4 비동기 업로드 조정 ──

    @Test
    @DisplayName("GET 요청 시 request body upload를 스킵한다 (body-less method)")
    void filter_getRequest_skipsRequestBodyUpload() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — req.header와 res 관련은 호출되지만, "req" type은 호출 안 됨
        verify(storageService, never()).upload(anyString(), any(byte[].class), eq("req"), anyInt());
        verify(storageService).upload(anyString(), any(byte[].class), eq("req.header"), anyInt());
    }

    @Test
    @DisplayName("POST 요청 시 downstream이 body를 읽으면 request body upload를 수행한다")
    void filter_postRequest_uploadsRequestBody() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/server-a/hello")
                .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap("body".getBytes())));
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // chain mock이 request body를 consume하도록 설정
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange mutated = invocation.getArgument(0);
            return mutated.getRequest().getBody().then();
        });

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — "req" type upload called
        verify(storageService).upload(anyString(), any(byte[].class), eq("req"), anyInt());
    }

    // ── 3.2 Hop Counter ──

    @Test
    @DisplayName("최초 요청 시 Redis INCR을 호출하여 hop=1을 반환한다")
    void filter_firstRequest_redisIncrReturnsHop1() {
        // given
        when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));

        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then
        verify(valueOps).increment(argThat(key -> key.startsWith("hop:")));
    }

    @Test
    @DisplayName("Redis hop 키에 TTL 5분을 설정한다")
    void filter_setsHopKeyTtlTo5Minutes() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then
        verify(redisTemplate).expire(argThat(key -> key.startsWith("hop:")), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("isNewTx=true일 때 필터 완료 후 Redis hop 키를 삭제한다")
    void filter_newTx_deletesRedisHopKey() {
        // given — no X-Tx-Id header → isNewTx = true
        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then
        verify(redisTemplate).delete(argThat((String key) -> key.startsWith("hop:")));
    }

    @Test
    @DisplayName("isNewTx=false일 때 Redis hop 키를 삭제하지 않는다")
    void filter_existingTx_doesNotDeleteRedisHopKey() {
        // given — X-Tx-Id present → isNewTx = false
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/server-a/hello")
                .header("X-Tx-Id", "existing-id")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then
        verify(redisTemplate, never()).delete(anyString());
    }

    // ── 3.5 메타데이터 구성 ──

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("정상 요청 시 메타데이터에 필수 필드(txId, hop, path, status, bodyUrl 등)가 포함된다")
    void filter_sendsMetadataWithRequiredFields() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(metadataSender).send(captor.capture());

        Map<String, Object> metadata = captor.getValue();
        assertThat(metadata).containsKeys("txId", "hop", "path", "status", "bodyUrl", "reqTime", "resTime", "duration");
        assertThat(metadata.get("path")).isEqualTo("/server-a/hello");
        assertThat(metadata.get("hop")).isEqualTo(1);
    }

    // ── 3.6 필터 순서 ──

    @Test
    @DisplayName("필터 순서가 HIGHEST_PRECEDENCE이다")
    void getOrder_returnsHighestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
