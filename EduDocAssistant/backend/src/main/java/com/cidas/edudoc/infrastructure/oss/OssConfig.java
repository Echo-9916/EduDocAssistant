package com.cidas.edudoc.infrastructure.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
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

    // ponytail: 客户端应用级单例，进程退出即释放；优雅停机需 OSSClient.shutdown() 时再加
    @Bean
    public OSS ossClient(OssProperties properties) {
        return new OSSClientBuilder()
                .build(properties.endpoint(), properties.accessKeyId(), properties.accessKeySecret());
    }
}
