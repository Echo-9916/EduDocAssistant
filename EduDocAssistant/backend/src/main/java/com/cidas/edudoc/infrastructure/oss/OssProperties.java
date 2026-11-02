package com.cidas.edudoc.infrastructure.oss;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** OSS 与 STS 配置（edudoc.oss.*），敏感项经环境变量注入，不落仓库。 */
@ConfigurationProperties("edudoc.oss")
public record OssProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKeyId,
        String accessKeySecret,
        String roleArn,
        String stsEndpoint,
        String objectKeyPrefix,
        long sessionTtlMinutes,
        long stsDurationSeconds) {
}
