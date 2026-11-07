package com.cidas.edudoc.template.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 格式校验 worker 配置（edudoc.format-check.*）。 */
@ConfigurationProperties("edudoc.format-check")
public record FormatCheckProperties(
        boolean enabled,
        int concurrency,
        String streamKey,
        String group) {
}
