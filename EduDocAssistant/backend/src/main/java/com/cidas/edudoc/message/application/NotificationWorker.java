package com.cidas.edudoc.message.application;

import com.cidas.edudoc.message.infrastructure.NotificationWorkerProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 信箱投递 worker（06 切片 2-4）：消费 `edudoc:stream:notification` 上的 NOTIFICATION_REQUESTED。
 * 骨架沿用 FormatCheckWorker（XGROUP MKSTREAM 幂等建组、pid consumer 名、XACK 兜底）。
 *
 * <p>去重键口径（设计 §4.3 的适配实现）：原文 {eventType}:{aggregateRef}:{recipientId} 在
 * dedup_key VARCHAR2(64) 里装不下两个 32 位 hex 主键，且 uk_notification_1 本身就是
 * (recipient_id, dedup_key)，收件人维度已由唯一键覆盖，故键取
 * {@code {notifType}:{aggregateRef}[:{dedupDate}]}——临近截止类含扫描日期，每天最多一封。
 * 重放/重复投递走 {@link InboxService#deliver} 的 false 分支，不产生第二封信。
 *
 * <p>偏好过滤在 deliver 内部（关掉某类返回 false 不写行），worker 不重复判断，只逐人调用。
 *
 * ponytail: 无 DLQ/pending 回收；单收件人失败记全栈日志后继续其余人，整条事件不重投
 *（与 FormatCheckWorker 口径一致，重放安全性由去重键兜底），要升级可靠性再加 XAUTOCLAIM。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWorker {

    private final StringRedisTemplate redis;
    private final InboxService inboxService;
    private final NotificationWorkerProperties properties;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean running;
    private ExecutorService pool;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.enabled() || !started.compareAndSet(false, true)) {
            if (properties.enabled()) {
                log.info("信箱投递 worker 已在运行，跳过重复启动");
            } else {
                log.info("信箱投递 worker 已禁用（edudoc.notification.enabled=false）");
            }
            return;
        }
        ensureGroup();
        running = true;
        pool = Executors.newFixedThreadPool(Math.max(1, properties.concurrency()), task -> {
            Thread thread = new Thread(task, "notification-worker");
            thread.setDaemon(true);
            return thread;
        });
        String instance = hostName() + "-" + ProcessHandle.current().pid();
        for (int i = 0; i < Math.max(1, properties.concurrency()); i++) {
            int index = i;
            pool.submit(() -> consumeLoop("notif-" + instance + "-" + index));
        }
        log.info("信箱投递 worker 已启动：stream={} group={} concurrency={}",
                properties.streamKey(), properties.group(), properties.concurrency());
    }

    @PreDestroy
    void shutdown() {
        running = false;
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    private void ensureGroup() {
        try {
            redis.opsForStream().createGroup(properties.streamKey(), ReadOffset.from("0"), properties.group());
        } catch (Exception e) {
            if (!isBusyGroup(e)) {
                throw e;
            }
        }
    }

    private static boolean isBusyGroup(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof io.lettuce.core.RedisBusyException
                    || String.valueOf(t.getMessage()).contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
    }

    private void consumeLoop(String consumerName) {
        StreamOperations<String, Object, Object> stream = redis.opsForStream();
        Consumer consumer = Consumer.from(properties.group(), consumerName);
        while (running) {
            List<MapRecord<String, Object, Object>> records;
            try {
                records = stream.read(consumer,
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(properties.streamKey(), ReadOffset.lastConsumed()));
            } catch (Exception e) {
                if (running) {
                    log.warn("信箱 Stream 读取失败，2s 后重试：{}", e.getMessage());
                    sleep(2000);
                }
                continue;
            }
            if (records == null || records.isEmpty()) {
                continue;
            }
            for (MapRecord<String, Object, Object> record : records) {
                handleRecord(stream, record);
            }
        }
    }

    private void handleRecord(StreamOperations<String, Object, Object> stream,
                              MapRecord<String, Object, Object> record) {
        try {
            process(String.valueOf(record.getValue().get("payload")));
        } catch (Exception e) {
            // 不吞栈：载荷损坏/解析失败记全栈后丢弃（重试无意义，重放语义由去重键保证）
            log.error("通知事件处理失败 record={}", record.getId(), e);
        } finally {
            try {
                stream.acknowledge(properties.streamKey(), properties.group(), record.getId());
            } catch (Exception e) {
                log.warn("XACK 失败 record={}", record.getId(), e);
            }
        }
    }

    /** 事件体处理（可测试入口，照 AiTaskWorker.processTask 口径）：解析载荷 → 拼去重键 → 逐人 deliver。 */
    public void process(String payloadJson) throws Exception {
        JsonNode node = objectMapper.readTree(payloadJson);
        String notifType = node.path("notifType").asText();
        String aggregateRef = node.path("aggregateRef").asText();
        if (notifType.isBlank() || aggregateRef.isBlank() || !node.path("recipientIds").isArray()) {
            log.error("通知事件载荷缺少必要字段，丢弃：{}", payloadJson);
            return;
        }
        String dedupDate = text(node, "dedupDate");
        // 去重键 = {notifType}:{aggregateRef}[:{dedupDate}]（§4.3 适配，见类注释）
        String dedupKey = dedupDate == null || dedupDate.isBlank()
                ? notifType + ":" + aggregateRef
                : notifType + ":" + aggregateRef + ":" + dedupDate;
        String title = node.path("title").asText();
        String body = text(node, "body");
        String resourceType = text(node, "resourceType");
        String resourceRef = text(node, "resourceRef");
        String actorId = text(node, "actorId");
        for (JsonNode recipient : node.path("recipientIds")) {
            String recipientId = recipient.asText();
            try {
                boolean written = inboxService.deliver(new InboxService.DeliverCommand(
                        recipientId, notifType, title, body, resourceType, resourceRef, actorId, dedupKey));
                if (!written) {
                    log.info("通知未写入（偏好关闭或去重键已存在）recipient={} dedupKey={}", recipientId, dedupKey);
                }
            } catch (Exception e) {
                // 单人失败不拖累其余收件人；不重投（重放即原结果，见类注释）
                log.error("通知投递失败 recipient={} dedupKey={}", recipientId, dedupKey, e);
            }
        }
    }

    /** 缺字段/JSON null 一律回 null（NullNode.asText() 会变 "null" 串，不能直接用）。 */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String hostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "local";
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
