package com.cidas.edudoc.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Outbox 调度器（概要 §3.3）：主应用内独立线程池扫描 NEW 事件投递 Redis Stream，指数退避重试。
 * taskId=eventId——不建独立任务表，任务状态最终事实 = edudoc_document.status（概要 §2.4）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    // ponytail: 单节点轮询不抢锁，多实例部署加 FOR UPDATE SKIP LOCKED
    private final OutboxEventRepository repository;
    private final StringRedisTemplate redis;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${edudoc.outbox.poll-interval-ms:1000}",
            initialDelayString = "${edudoc.outbox.poll-interval-ms:1000}")
    public void poll() {
        if (!properties.enabled()) {
            return;
        }
        for (OutboxEvent event : repository.findPublishable(properties.batchSize())) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        String streamKey = properties.streamKeys().get(event.getEventType());
        if (streamKey == null) {
            log.error("事件类型 {} 无 Stream 路由，置 FAILED", event.getEventType());
            repository.markFailed(event.getId());
            return;
        }
        try {
            redis.opsForStream().add(streamKey, Map.of(
                    "eventId", event.getId(),
                    "eventType", event.getEventType(),
                    "taskId", event.getId(),
                    "aggregateType", event.getAggregateType(),
                    "aggregateRef", event.getAggregateRef(),
                    "schemaVersion", "1",
                    "payload", event.getPayloadJson()));
            repository.markPublished(event.getId());
        } catch (Exception e) {
            int attempts = event.getRetryCount() + 1;
            if (attempts >= properties.maxRetry()) {
                log.error("事件 {} 投递 {} 次失败，置 FAILED：{}", event.getId(), attempts, e.getMessage());
                repository.markFailed(event.getId());
            } else {
                long delay = Math.min(600, 10L << event.getRetryCount());
                log.warn("事件 {} 投递失败，{}s 后第 {} 次重试：{}", event.getId(), delay, attempts, e.getMessage());
                repository.markRetry(event.getId(), delay);
            }
        }
    }
}
