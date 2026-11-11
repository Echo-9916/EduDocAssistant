package com.cidas.edudoc.message.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 信箱投递 worker 配置（edudoc.notification.*），照 FormatCheckProperties 形状。 */
@ConfigurationProperties("edudoc.notification")
public record NotificationWorkerProperties(
        boolean enabled,
        int concurrency,
        String streamKey,
        String group) {
}
