package com.cidas.edudoc.ai.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 问答校准参数（edudoc.qa.*）：evidence-threshold 为物理世界校准口（默认 0.35，可调）。 */
@ConfigurationProperties("edudoc.qa")
public record QaProperties(double evidenceThreshold) {
}
