package com.cidas.edudoc.infrastructure.oss;

import com.aliyun.sts20150401.models.AssumeRoleRequest;
import com.aliyun.sts20150401.models.AssumeRoleResponse;
import com.aliyun.sts20150401.models.AssumeRoleResponseBody;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** OSS 适配：浏览器直传的短期 STS 签发（inline policy 收窄到单个 objectKey，概要 §5.4 不暴露长期凭据）。 */
@Component
@RequiredArgsConstructor
public class OssStorageAdapter {

    private final OssProperties properties;
    private final com.aliyun.sts20150401.Client stsClient;

    public UploadCredentials issueUploadCredentials(String objectKey, String roleSessionName) {
        String policy = """
                {"Version":"1","Statement":[{"Effect":"Allow","Action":[
                "oss:PutObject","oss:InitiateMultipartUpload","oss:UploadPart",
                "oss:CompleteMultipartUpload","oss:AbortMultipartUpload","oss:ListParts"],
                "Resource":"acs:oss:*:*:%s/%s"}]}"""
                .formatted(properties.bucket(), objectKey);
        try {
            AssumeRoleResponse response = stsClient.assumeRole(new AssumeRoleRequest()
                    .setRoleArn(properties.roleArn())
                    .setRoleSessionName(roleSessionName)
                    .setPolicy(policy)
                    .setDurationSeconds(properties.stsDurationSeconds()));
            AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials c = response.getBody().getCredentials();
            return new UploadCredentials(c.getAccessKeyId(), c.getAccessKeySecret(), c.getSecurityToken(),
                    c.getExpiration(), properties.bucket(), properties.region(), properties.endpoint());
        } catch (Exception e) {
            throw new CidasBusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "上传凭据签发失败：" + e.getMessage(), true, java.util.Map.of());
        }
    }

    /** 浏览器直传所需的全部信息（STS 临时凭据 + 目标对象位置）。 */
    public record UploadCredentials(String accessKeyId, String accessKeySecret, String securityToken,
                                    String expiration, String bucket, String region, String endpoint) {
    }
}
