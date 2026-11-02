package com.cidas.edudoc.common.outbox;

import com.cidas.edudoc.common.persistence.Ids;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Map;

/** 事件写入端口：调用方事务内提交，B-05 调度器负责投递。 */
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
        mapper.insert(new OutboxEvent(id, aggregateType, aggregateRef, eventType, json));
        return id;
    }
}
