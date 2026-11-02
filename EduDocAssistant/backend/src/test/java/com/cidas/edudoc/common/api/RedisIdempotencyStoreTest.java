package com.cidas.edudoc.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisIdempotencyStoreTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final RedisIdempotencyStore store = new RedisIdempotencyStore(redis);

    RedisIdempotencyStoreTest() {
        when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    void firstSubmitAcquires() {
        when(ops.setIfAbsent(eq("edudoc:idem:k1"), eq("1"), any(Duration.class))).thenReturn(true);
        assertThat(store.tryAcquire("k1")).isTrue();
    }

    @Test
    void replayRejected() {
        when(ops.setIfAbsent(eq("edudoc:idem:k1"), eq("1"), any(Duration.class))).thenReturn(false);
        assertThat(store.tryAcquire("k1")).isFalse();
    }

    @Test
    void redisFailureCountsAsDuplicate() {
        when(ops.setIfAbsent(eq("edudoc:idem:k2"), eq("1"), any(Duration.class))).thenReturn(null);
        assertThat(store.tryAcquire("k2")).isFalse();
    }
}
