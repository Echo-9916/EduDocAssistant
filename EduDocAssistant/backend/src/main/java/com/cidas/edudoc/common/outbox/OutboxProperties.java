package com.cidas.edudoc.common.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/** Outbox 调度配置（edudoc.outbox.*）；stream-keys 是事件类型 → Redis Stream 的路由表。 */
@ConfigurationProperties("edudoc.outbox")
public record OutboxProperties(
        boolean enabled,
        long pollIntervalMs,
        int batchSize,
        int maxRetry,
        Map<String, String> streamKeys) {
}
