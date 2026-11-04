package com.cidas.edudoc.ai.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 向量化 worker 配置（edudoc.embed.*）：独立线程池与消费组，与解析 worker 互不抢占。 */
@ConfigurationProperties("edudoc.embed")
public record EmbedProperties(
        boolean enabled,
        int concurrency,
        int batchSize,
        String streamKey,
        String group) {
}
