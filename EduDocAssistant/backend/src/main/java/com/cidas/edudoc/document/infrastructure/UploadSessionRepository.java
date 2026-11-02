package com.cidas.edudoc.document.infrastructure;

import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/** 上传会话存取（Redis JSON，TTL=edudoc.oss.session-ttl-minutes）。 */
@Repository
@RequiredArgsConstructor
public class UploadSessionRepository {

    private static final String PREFIX = "edudoc:upsession:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final OssProperties ossProperties;

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
