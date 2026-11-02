package com.cidas.edudoc.common.api;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Redis 幂等键去重：SET NX + 24h TTL，重启不丢、多实例共享。 */
@Component
@RequiredArgsConstructor
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String PREFIX = "edudoc:idem:";
    private static final Duration TTL = Duration.ofDays(1);

    private final StringRedisTemplate redis;

    @Override
    public boolean tryAcquire(String key) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX + key, "1", TTL));
    }
}
