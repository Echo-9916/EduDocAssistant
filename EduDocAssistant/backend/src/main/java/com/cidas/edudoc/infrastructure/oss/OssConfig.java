package com.cidas.edudoc.infrastructure.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * OSS/STS 客户端装配；长期凭据只出现在服务端配置。
 * 均为 @Lazy：凭据缺失/错误不得炸掉启动（登录、列表等与 OSS 无关的路径照常可用），
 * 首次真正使用时才构建，由 OssStorageAdapter 转成可读的 50301。
 */
@Configuration
public class OssConfig {

    @Bean
    @Lazy
    public com.aliyun.sts20150401.Client stsClient(OssProperties properties) throws Exception {
        Config config = new Config()
                .setAccessKeyId(properties.accessKeyId())
                .setAccessKeySecret(properties.accessKeySecret())
                .setEndpoint(properties.stsEndpoint());
        return new com.aliyun.sts20150401.Client(config);
    }

    @Bean
    @Lazy
    public OSS ossClient(OssProperties properties) {
        return new OSSClientBuilder()
                .build(properties.endpoint(), properties.accessKeyId(), properties.accessKeySecret());
    }
}
