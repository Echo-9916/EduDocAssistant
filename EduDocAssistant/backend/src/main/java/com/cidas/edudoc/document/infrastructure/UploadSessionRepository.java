package com.cidas.edudoc.document.infrastructure;

import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** 上传会话存取（Redis JSON，TTL=edudoc.oss.session-ttl-minutes）。 */
@Repository
@RequiredArgsConstructor
public class UploadSessionRepository {

    private static final String PREFIX = "edudoc:upsession:";

    /** 值比较交换（即详细设计 uploadSessionRepo.lock 的 Redis 落地）：旧 JSON 未变才写入新值。 */
    private static final RedisScript<Long> CAS_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3]) return 1 else return 0 end", Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final OssProperties ossProperties;

    /** 乐观并发：expected 仍为当前值时替换为 next 并重置 TTL；冲突返回 false。 */
    public boolean compareAndSet(UploadSession expected, UploadSession next) {
        Long done = redis.execute(CAS_SCRIPT, List.of(PREFIX + expected.id()),
                toJson(expected), toJson(next),
                String.valueOf(Duration.ofMinutes(ossProperties.sessionTtlMinutes()).toMillis()));
        return done != null && done == 1L;
    }

    public void save(UploadSession session) {
        redis.opsForValue().set(PREFIX + session.id(), toJson(session),
                Duration.ofMinutes(ossProperties.sessionTtlMinutes()));
    }

    public Optional<UploadSession> find(String id) {
        String json = redis.opsForValue().get(PREFIX + id);
        return json == null ? Optional.empty() : Optional.of(fromJson(json));
    }

    private String toJson(UploadSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("上传会话序列化失败", e);
        }
    }

    private UploadSession fromJson(String json) {
        try {
            return objectMapper.readValue(json, UploadSession.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("上传会话反序列化失败", e);
        }
    }
}
