package org.example.springcloudgatwaylab.filter;

import org.example.springcloudgatwaylab.service.KafkaMetadataSender;
import org.example.springcloudgatwaylab.service.LogStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LoggingGlobalFilter - 필터 체인 로직")
class LoggingGlobalFilterTest {

    private LogStorageService storageService;
    private KafkaMetadataSender metadataSender;
    private HopTracker hopTracker;
    private GatewayFilterChain chain;
    private LoggingGlobalFilter filter;

    @BeforeEach
    void setUp() {
        storageService = mock(LogStorageService.class);
        metadataSender = mock(KafkaMetadataSender.class);
        hopTracker = mock(HopTracker.class);
        chain = mock(GatewayFilterChain.class);

        when(hopTracker.increment(anyString())).thenReturn(Mono.just(new HopTracker.HopResult(1, false)));
        when(hopTracker.delete(anyString())).thenReturn(Mono.empty());
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(storageService.getStorageBaseUrl(anyString(), anyInt())).thenReturn("s3://bucket/path");

        filter = new LoggingGlobalFilter(storageService, metadataSender, hopTracker, 1024 * 1024, "/actuator/**");
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
        verify(hopTracker, never()).delete(anyString());
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
    @DisplayName("최초 요청 시 HopTracker.increment()가 호출된다")
    void filter_firstRequest_hopTrackerIncrementCalled() {
        // given — setUp에서 hop=1 기본값 반환
        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then
        verify(hopTracker).increment(anyString());
    }

    @Test
    @DisplayName("isNewTx=true일 때 필터 완료 후 HopTracker.delete()가 호출된다")
    void filter_newTx_hopTrackerDeleteCalled() {
        // given — no X-Tx-Id header → isNewTx = true
        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then
        verify(hopTracker).delete(anyString());
    }

    @Test
    @DisplayName("isNewTx=false일 때 HopTracker.delete()가 호출되지 않는다")
    void filter_existingTx_hopTrackerDeleteNotCalled() {
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
        verify(hopTracker, never()).delete(anyString());
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

    // ── Phase 4: 전체 흐름 통합 ──

    @Test
    @DisplayName("POST 요청 시 req body, req.header, res.header를 업로드하고 Kafka 메타데이터를 전송한다")
    void filter_postRequest_uploadsObjectsAndSendsMetadata() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/server-a/data")
                .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap("request-body".getBytes())));
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // chain이 body를 consume하도록 설정
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange mutated = invocation.getArgument(0);
            return mutated.getRequest().getBody().then();
        });

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — req body, req.header, res.header 업로드 (res body는 mock이라 비어있어 스킵)
        verify(storageService).upload(anyString(), any(byte[].class), eq("req"), anyInt());
        verify(storageService).upload(anyString(), any(byte[].class), eq("req.header"), anyInt());
        verify(storageService).upload(anyString(), any(byte[].class), eq("res.header"), anyInt());
        // Kafka metadata 전송
        verify(metadataSender).send(any(Map.class));
    }

    @Test
    @DisplayName("GET 요청 시 req body를 스킵하고 3개 객체만 업로드한다")
    void filter_getRequest_uploads3ObjectsSkipsReqBody() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — req body 스킵, 나머지 3개 업로드
        verify(storageService, never()).upload(anyString(), any(byte[].class), eq("req"), anyInt());
        verify(storageService).upload(anyString(), any(byte[].class), eq("req.header"), anyInt());
        verify(storageService).upload(anyString(), any(byte[].class), eq("res.header"), anyInt());
        // Kafka metadata 전송
        verify(metadataSender).send(any(Map.class));
    }

    @Test
    @DisplayName("동일 txId로 연속 요청 시 HopTracker.increment()가 2번 호출된다")
    void filter_sameExistingTxId_hopTrackerIncrementCalledTwice() {
        // given — 두 번째 요청은 hop=2
        when(hopTracker.increment(eq("shared-tx")))
                .thenReturn(Mono.just(new HopTracker.HopResult(1, false)))
                .thenReturn(Mono.just(new HopTracker.HopResult(2, false)));

        MockServerHttpRequest req1 = MockServerHttpRequest
                .get("/server-a/chain").header("X-Tx-Id", "shared-tx").build();
        MockServerHttpRequest req2 = MockServerHttpRequest
                .get("/server-b/chain").header("X-Tx-Id", "shared-tx").build();

        // when
        StepVerifier.create(filter.filter(MockServerWebExchange.from(req1), chain))
                .verifyComplete();
        StepVerifier.create(filter.filter(MockServerWebExchange.from(req2), chain))
                .verifyComplete();

        // then — HopTracker.increment() 2번, delete 없음 (isNewTx=false)
        verify(hopTracker, times(2)).increment(eq("shared-tx"));
        verify(hopTracker, never()).delete(anyString());
    }

    @Test
    @DisplayName("StorageService 업로드 실패해도 필터는 정상 완료된다")
    void filter_uploadFailure_filterStillCompletes() {
        // given
        doThrow(new RuntimeException("MinIO down")).when(storageService)
                .upload(anyString(), any(byte[].class), anyString(), anyInt());

        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when & then — 예외 전파 없이 완료
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("모든 업로드 실패 시 Kafka 메타데이터에 bodyUrl=null이 전송된다")
    void filter_allUploadsFail_bodyUrlIsNullInMetadata() {
        // given — 모든 upload 호출에서 예외 발생
        doThrow(new RuntimeException("MinIO down")).when(storageService)
                .upload(anyString(), any(byte[].class), anyString(), anyInt());

        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — bodyUrl=null (존재하지 않는 URL을 batch-server가 조회하지 못하도록)
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(metadataSender).send(captor.capture());
        assertThat(captor.getValue().get("bodyUrl")).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("업로드 성공 시 Kafka 메타데이터에 실제 bodyUrl이 전송된다")
    void filter_uploadSucceeds_bodyUrlPresentInMetadata() {
        // given — 기본 setUp: storageService.upload()는 예외 없음
        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — bodyUrl이 null이 아니어야 함
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(metadataSender).send(captor.capture());
        assertThat(captor.getValue().get("bodyUrl")).isEqualTo("s3://bucket/path");
    }

    @Test
    @DisplayName("필터 체인 예외 발생 시 에러가 전파되고 ERROR_ATTRIBUTE에 메시지가 저장된다")
    void filter_chainException_errorPropagatesAndAttributeSet() {
        // given
        when(chain.filter(any())).thenReturn(Mono.error(new RuntimeException("Backend error")));

        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when & then — 에러가 전파됨 (doOnError는 관찰만 함)
        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(RuntimeException.class)
                .verify();

        // ERROR_ATTRIBUTE에 메시지 저장 확인
        assertThat((String) exchange.getAttribute("LOG_ERROR_MSG")).isEqualTo("Backend error");
    }

    @Test
    @DisplayName("Kafka send 예외 시 필터는 정상 완료된다 (graceful degradation)")
    void filter_kafkaSendException_filterStillCompletes() {
        // given — metadataSender.send()에서 미처리 예외 발생
        doThrow(new RuntimeException("Kafka down")).when(metadataSender).send(any());

        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when & then — try-catch로 감싸져 있으므로 예외가 전파되지 않음
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("HopTracker가 hop=1 폴백을 반환하면 hop=1로 storageService 업로드가 호출된다")
    void filter_hopTrackerFallbackHop1_usedInUpload() {
        // given — HopTracker가 Redis 장애 폴백 결과 반환
        when(hopTracker.increment(anyString()))
                .thenReturn(Mono.just(new HopTracker.HopResult(1, true)));

        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — hop=1로 업로드 호출 (hop=0 충돌 방지)
        verify(storageService).upload(anyString(), any(byte[].class), eq("req.header"), eq(1));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("HopTracker가 redisError=true를 반환하면 메타데이터에 redisError=true 플래그가 포함된다")
    void filter_hopTrackerRedisError_metadataContainsRedisErrorFlag() {
        // given — HopTracker가 redisError=true 반환
        when(hopTracker.increment(anyString()))
                .thenReturn(Mono.just(new HopTracker.HopResult(1, true)));

        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — 메타데이터에 redisError=true 포함 확인
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(metadataSender).send(captor.capture());
        assertThat(captor.getValue()).containsEntry("redisError", true);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("HopTracker가 redisError=false를 반환하면 메타데이터에 redisError 플래그가 없다")
    void filter_hopTrackerNoRedisError_metadataHasNoRedisErrorFlag() {
        // given — setUp 기본값: HopResult(1, false)
        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — redisError 키 없음
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(metadataSender).send(captor.capture());
        assertThat(captor.getValue()).doesNotContainKey("redisError");
    }

    @Test
    @DisplayName("HopTracker가 hop=3을 반환하면 hop=3으로 storageService가 호출된다")
    void filter_hopTrackerReturnsHop3_usedInUpload() {
        // given — HopTracker hop=3 반환
        when(hopTracker.increment(anyString()))
                .thenReturn(Mono.just(new HopTracker.HopResult(3, false)));

        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — hop=3으로 업로드 확인
        verify(storageService).upload(anyString(), any(byte[].class), eq("req.header"), eq(3));
    }

    // ── Phase 6: Edge Cases & Resilience ──

    @Test
    @DisplayName("1MB 이상 큰 요청 본문이 정상적으로 캡처되어 업로드된다")
    void filter_largeRequestBody_capturedAndUploaded() {
        // given — 1MB payload split into multiple DataBuffer chunks
        byte[] chunk = new byte[256 * 1024]; // 256KB
        java.util.Arrays.fill(chunk, (byte) 'A');
        Flux<org.springframework.core.io.buffer.DataBuffer> bodyFlux = Flux.range(0, 4)
                .map(i -> DefaultDataBufferFactory.sharedInstance.wrap(chunk.clone()));

        MockServerHttpRequest request = MockServerHttpRequest
                .post("/server-a/upload")
                .body(bodyFlux);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // chain이 body를 consume
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange mutated = invocation.getArgument(0);
            return mutated.getRequest().getBody().then();
        });

        // capture uploaded bytes
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — "req" type으로 1MB 데이터 업로드 확인
        verify(storageService).upload(anyString(), dataCaptor.capture(), eq("req"), anyInt());
        assertThat(dataCaptor.getValue()).hasSize(1024 * 1024); // 1MB
    }

    @Test
    @DisplayName("1MB 이상 큰 응답 본문이 정상적으로 캡처되어 업로드된다")
    void filter_largeResponseBody_capturedAndUploaded() {
        // given — 1MB response payload in 4 chunks
        byte[] chunk = new byte[256 * 1024]; // 256KB
        java.util.Arrays.fill(chunk, (byte) 'B');

        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/big-response").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // chain이 response에 1MB 데이터를 write
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange mutated = invocation.getArgument(0);
            Flux<org.springframework.core.io.buffer.DataBuffer> responseBody = Flux.range(0, 4)
                    .map(i -> DefaultDataBufferFactory.sharedInstance.wrap(chunk.clone()));
            return mutated.getResponse().writeWith(responseBody);
        });

        // capture uploaded bytes
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — "res" type으로 1MB 데이터 업로드 확인
        verify(storageService).upload(anyString(), dataCaptor.capture(), eq("res"), anyInt());
        assertThat(dataCaptor.getValue()).hasSize(1024 * 1024); // 1MB
    }

    @Test
    @DisplayName("동시 다중 요청 시 각 요청의 ByteArrayOutputStream이 격리되어 body가 섞이지 않는다")
    void filter_concurrentRequests_bodyBuffersAreIsolated() {
        // given — 서로 다른 body를 가진 두 요청
        byte[] bodyA = "AAAA-request-body".getBytes();
        byte[] bodyB = "BBBB-request-body".getBytes();

        MockServerHttpRequest requestA = MockServerHttpRequest
                .post("/server-a/data")
                .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bodyA)));
        MockServerHttpRequest requestB = MockServerHttpRequest
                .post("/server-b/data")
                .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bodyB)));

        MockServerWebExchange exchangeA = MockServerWebExchange.from(requestA);
        MockServerWebExchange exchangeB = MockServerWebExchange.from(requestB);

        // chain이 body를 consume
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange mutated = invocation.getArgument(0);
            return mutated.getRequest().getBody().then();
        });

        // when — 두 요청을 동시에 실행
        Mono<Void> filterA = filter.filter(exchangeA, chain);
        Mono<Void> filterB = filter.filter(exchangeB, chain);

        StepVerifier.create(Mono.when(filterA, filterB))
                .verifyComplete();

        // then — "req" type 업로드가 2번 호출되고, 각각 올바른 body를 가짐
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(storageService, times(2)).upload(anyString(), dataCaptor.capture(), eq("req"), anyInt());

        java.util.List<byte[]> capturedBodies = dataCaptor.getAllValues();
        assertThat(capturedBodies).hasSize(2);
        // 두 body가 서로 다르고, 각각 원본과 일치
        assertThat(new String(capturedBodies.get(0))).isIn("AAAA-request-body", "BBBB-request-body");
        assertThat(new String(capturedBodies.get(1))).isIn("AAAA-request-body", "BBBB-request-body");
        assertThat(new String(capturedBodies.get(0))).isNotEqualTo(new String(capturedBodies.get(1)));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("인코딩된 특수문자가 포함된 경로가 메타데이터에 정상적으로 포함된다")
    void filter_encodedPathChars_metadataContainsPath() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/server-a/%ED%99%8D%EA%B8%B8%EB%8F%99?q=%EC%95%88%EB%85%95")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(metadataSender).send(captor.capture());

        Map<String, Object> metadata = captor.getValue();
        assertThat((String) metadata.get("path")).contains("/server-a/");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("204 No Content 응답 시 빈 body로 업로드하고 메타데이터를 정상 전송한다")
    void filter_204NoContent_uploadsEmptyBodyAndSendsMetadata() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/no-content").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // chain이 204 응답을 반환 (body 없음)
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange mutated = invocation.getArgument(0);
            mutated.getResponse().setStatusCode(org.springframework.http.HttpStatus.NO_CONTENT);
            return Mono.empty();
        });

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — res body는 비어있으므로 upload 스킵 (uploadDataAsync에서 빈 배열은 skip)
        verify(storageService, never()).upload(anyString(), any(byte[].class), eq("res"), anyInt());
        // 메타데이터는 정상 전송
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(metadataSender).send(captor.capture());
        assertThat(captor.getValue().get("status")).isEqualTo(204);
    }

    @Test
    @DisplayName("HEAD 요청 시 request body 캡처를 생략한다")
    void filter_headRequest_skipsRequestBodyCapture() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest.head("/server-a/resource").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — HEAD는 body-less method → req body upload 스킵
        verify(storageService, never()).upload(anyString(), any(byte[].class), eq("req"), anyInt());
        // req.header는 업로드됨
        verify(storageService).upload(anyString(), any(byte[].class), eq("req.header"), anyInt());
        // 메타데이터 전송됨
        verify(metadataSender).send(any(Map.class));
    }

    // ── Phase 1.2: Health Check 경로 Skip ──

    @Test
    @DisplayName("/actuator/health 요청 시 Redis/MinIO/Kafka 호출 없이 chain만 통과한다")
    void filter_actuatorHealthPath_skipsLoggingPipeline() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — HopTracker, MinIO, Kafka 모두 호출되지 않음
        verify(hopTracker, never()).increment(anyString());
        verify(storageService, never()).upload(anyString(), any(byte[].class), anyString(), anyInt());
        verify(metadataSender, never()).send(any());
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("/actuator/health/liveness 요청도 skip 패턴에 매칭되어 로깅 파이프라인을 건너뛴다")
    void filter_actuatorHealthLivenessPath_skipsLoggingPipeline() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health/liveness").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then
        verify(hopTracker, never()).increment(anyString());
        verify(storageService, never()).upload(anyString(), any(byte[].class), anyString(), anyInt());
        verify(metadataSender, never()).send(any());
    }

    @Test
    @DisplayName("/server-a/** 요청은 skip 경로에 해당하지 않아 정상 로깅된다")
    void filter_serverAPath_notSkipped_loggingPipelineExecuted() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest.get("/server-a/hello").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then — 정상 로깅 파이프라인 실행 확인
        verify(hopTracker).increment(anyString());
        verify(metadataSender).send(any());
    }

    @Test
    @DisplayName("skip-paths에 여러 패턴이 쉼표로 구분되어 있으면 모두 매칭된다")
    void filter_multipleSkipPatterns_allMatched() {
        // given — skip-paths에 /actuator/** 와 /healthz 추가
        LoggingGlobalFilter multiSkipFilter = new LoggingGlobalFilter(
                storageService, metadataSender, hopTracker, 1024 * 1024, "/actuator/**,/healthz");

        MockServerHttpRequest actuatorReq = MockServerHttpRequest.get("/actuator/ready").build();
        MockServerHttpRequest healthzReq = MockServerHttpRequest.get("/healthz").build();

        // when & then — 두 경로 모두 skip
        StepVerifier.create(multiSkipFilter.filter(MockServerWebExchange.from(actuatorReq), chain))
                .verifyComplete();
        StepVerifier.create(multiSkipFilter.filter(MockServerWebExchange.from(healthzReq), chain))
                .verifyComplete();

        // Redis, MinIO, Kafka 호출 없음
        verify(hopTracker, never()).increment(anyString());
        verify(storageService, never()).upload(anyString(), any(byte[].class), anyString(), anyInt());
        verify(metadataSender, never()).send(any());
    }

    @Test
    @DisplayName("동시 요청 시 각 요청에 서로 다른 txId가 생성된다")
    void filter_concurrentRequests_uniqueTxIds() {
        // given — X-Tx-Id 헤더 없는 3개 요청 (각각 새 UUID 생성)
        java.util.List<String> capturedTxIds = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange mutated = invocation.getArgument(0);
            String txId = mutated.getRequest().getHeaders().getFirst("X-Tx-Id");
            capturedTxIds.add(txId);
            return Mono.empty();
        });

        MockServerWebExchange ex1 = MockServerWebExchange.from(MockServerHttpRequest.get("/server-a/1").build());
        MockServerWebExchange ex2 = MockServerWebExchange.from(MockServerHttpRequest.get("/server-a/2").build());
        MockServerWebExchange ex3 = MockServerWebExchange.from(MockServerHttpRequest.get("/server-a/3").build());

        // when — 동시 실행
        StepVerifier.create(Mono.when(
                        filter.filter(ex1, chain),
                        filter.filter(ex2, chain),
                        filter.filter(ex3, chain)))
                .verifyComplete();

        // then — 3개 모두 고유한 UUID
        assertThat(capturedTxIds).hasSize(3);
        assertThat(new java.util.HashSet<>(capturedTxIds)).hasSize(3); // 중복 없음
        capturedTxIds.forEach(txId ->
                assertThat(txId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }
}
