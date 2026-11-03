package com.cidas.edudoc.document.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 解析 worker 配置（edudoc.parse.*）。 */
@ConfigurationProperties("edudoc.parse")
public record ParseProperties(
        boolean enabled,
        int concurrency,
        String streamKey,
        String group,
        int chunkSize) {
}
