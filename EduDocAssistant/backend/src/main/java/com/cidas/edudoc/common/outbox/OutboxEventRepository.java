package com.cidas.edudoc.common.outbox;

import com.cidas.edudoc.common.persistence.Ids;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** 事件写入端口：调用方事务内提交，{@link OutboxDispatcher} 负责投递。 */
@Repository
@RequiredArgsConstructor
public class OutboxEventRepository {

    private final OutboxEventMapper mapper;
    private final ObjectMapper objectMapper;

    public String publish(String aggregateType, String aggregateRef, String eventType, Map<String, Object> payload) {
        String id = Ids.hex16();
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Outbox 负载序列化失败", e);
        }
        mapper.insert(new OutboxEvent(id, aggregateType, aggregateRef, eventType, json, 0, "NEW"));
        return id;
    }

    public List<OutboxEvent> findPublishable(int batchSize) {
        return mapper.selectPublishable(batchSize);
    }

    public void markPublished(String id) {
        mapper.markPublished(id);
    }

    public void markRetry(String id, long delaySeconds) {
        mapper.markRetry(id, delaySeconds);
    }

    public void markFailed(String id) {
        mapper.markFailed(id);
    }
}
