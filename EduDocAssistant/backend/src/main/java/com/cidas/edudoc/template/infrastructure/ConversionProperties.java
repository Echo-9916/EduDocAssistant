package com.cidas.edudoc.template.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 格式转换 worker 配置（edudoc.conversion.*）。 */
@ConfigurationProperties("edudoc.conversion")
public record ConversionProperties(
        boolean enabled,
        int concurrency,
        String streamKey,
        String group,
        long timeoutMs) {
}
