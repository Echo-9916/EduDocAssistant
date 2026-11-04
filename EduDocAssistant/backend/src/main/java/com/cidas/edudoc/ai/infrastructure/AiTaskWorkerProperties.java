package com.cidas.edudoc.ai.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI 生成任务 worker 配置（edudoc.ai-task.*）：timeout-seconds 为单任务预算（P-04 60s），group-chars 为摘要分组上限。 */
@ConfigurationProperties("edudoc.ai-task")
public record AiTaskWorkerProperties(
        boolean enabled,
        int concurrency,
        String streamKey,
        String group,
        long timeoutSeconds,
        int groupChars) {
}
