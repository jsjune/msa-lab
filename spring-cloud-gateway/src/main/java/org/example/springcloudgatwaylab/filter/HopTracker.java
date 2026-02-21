package org.example.springcloudgatwaylab.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis 기반 hop 카운터 관리.
 * INCR/TTL/삭제 로직을 캡슐화하고 Redis 장애 시 폴백(hop=1)을 처리한다.
 */
@Component
public class HopTracker {

    private static final Logger logger = LoggerFactory.getLogger(HopTracker.class);
    static final String HOP_KEY_PREFIX = "hop:";
    static final Duration HOP_KEY_TTL = Duration.ofMinutes(5);

    private final ReactiveStringRedisTemplate redisTemplate;

    public HopTracker(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * hop 카운터를 원자적으로 증가시키고 결과를 반환한다.
     * Redis 장애 시 hop=1 폴백값과 redisError=true 플래그를 반환한다.
     */
    public Mono<HopResult> increment(String txId) {
        String hopKey = HOP_KEY_PREFIX + txId;
        return redisTemplate.opsForValue().increment(hopKey)
                .flatMap(hopLong ->
                        redisTemplate.expire(hopKey, HOP_KEY_TTL)
                                .onErrorResume(e -> {
                                    logger.warn("Failed to set TTL for hop key: {}", hopKey, e);
                                    return Mono.just(true);
                                })
                                .thenReturn(new HopResult(hopLong.intValue(), false))
                )
                .onErrorResume(e -> {
                    logger.warn("Redis unavailable, fallback hop=1: txId={}", txId, e);
                    return Mono.just(new HopResult(1, true));
                });
    }

    /**
     * hop 키를 삭제한다. 최초 진입 txId 처리 완료 후 호출.
     */
    public Mono<Void> delete(String txId) {
        String hopKey = HOP_KEY_PREFIX + txId;
        return redisTemplate.delete(hopKey)
                .onErrorResume(e -> {
                    logger.warn("Failed to delete hop key: {}", hopKey, e);
                    return Mono.just(0L);
                })
                .then();
    }

    /**
     * hop 증가 결과. hop 값과 Redis 오류 여부를 함께 전달한다.
     */
    public record HopResult(int hop, boolean redisError) {}
}
