package org.example.springcloudgatwaylab.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("HopTracker - Redis hop 카운터 관리")
class HopTrackerTest {

    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;
    private HopTracker hopTracker;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        hopTracker = new HopTracker(redisTemplate);
    }

    @Test
    @DisplayName("Redis INCR 성공 시 hop 값을 반환하고 redisError=false이다")
    void increment_redisSuccess_returnsHopAndNoError() {
        // given
        when(valueOps.increment(anyString())).thenReturn(Mono.just(3L));

        // when & then
        StepVerifier.create(hopTracker.increment("tx-123"))
                .assertNext(result -> {
                    assertThat(result.hop()).isEqualTo(3);
                    assertThat(result.redisError()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Redis INCR 실패 시 hop=1 폴백과 redisError=true를 반환한다")
    void increment_redisFailure_fallbackHop1AndRedisErrorTrue() {
        // given
        when(valueOps.increment(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

        // when & then
        StepVerifier.create(hopTracker.increment("tx-123"))
                .assertNext(result -> {
                    assertThat(result.hop()).isEqualTo(1);
                    assertThat(result.redisError()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("increment 시 hop 키에 5분 TTL을 설정한다")
    void increment_setsTtl5Minutes() {
        // when
        StepVerifier.create(hopTracker.increment("tx-123"))
                .assertNext(result -> assertThat(result.hop()).isEqualTo(1))
                .verifyComplete();

        // then
        verify(redisTemplate).expire(eq("hop:tx-123"), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("TTL 설정 실패해도 INCR 성공값으로 정상 완료된다")
    void increment_ttlFailure_stillReturnsHopFromIncr() {
        // given
        when(valueOps.increment(anyString())).thenReturn(Mono.just(2L));
        when(redisTemplate.expire(anyString(), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("TTL fail")));

        // when & then
        StepVerifier.create(hopTracker.increment("tx-123"))
                .assertNext(result -> {
                    assertThat(result.hop()).isEqualTo(2);
                    assertThat(result.redisError()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("delete 호출 시 hop:{txId} 키를 삭제한다")
    void delete_callsRedisDeleteWithHopKey() {
        // when
        StepVerifier.create(hopTracker.delete("tx-123"))
                .verifyComplete();

        // then
        verify(redisTemplate).delete("hop:tx-123");
    }

    @Test
    @DisplayName("delete Redis 실패해도 Mono.empty()로 정상 완료된다")
    void delete_redisFailure_completesNormally() {
        // given
        when(redisTemplate.delete(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

        // when & then
        StepVerifier.create(hopTracker.delete("tx-123"))
                .verifyComplete();
    }
}
