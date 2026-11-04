package com.cidas.edudoc.ai.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** ai-service 客户端配置（edudoc.ai-service.*）；internal-token 只走环境变量，不入仓。 */
@ConfigurationProperties("edudoc.ai-service")
public record AiServiceProperties(
        String baseUrl,
        String internalToken,
        long shortTimeoutMs,
        long longTimeoutMs,
        long answerTimeoutMs) {
}
