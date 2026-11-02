package com.cidas.edudoc.infrastructure.oss;

import com.aliyun.teaopenapi.models.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OSS/STS 客户端装配；长期凭据只出现在服务端配置。 */
@Configuration
public class OssConfig {

    @Bean
    public com.aliyun.sts20150401.Client stsClient(OssProperties properties) throws Exception {
        Config config = new Config()
                .setAccessKeyId(properties.accessKeyId())
                .setAccessKeySecret(properties.accessKeySecret())
                .setEndpoint(properties.stsEndpoint());
        return new com.aliyun.sts20150401.Client(config);
    }
}
