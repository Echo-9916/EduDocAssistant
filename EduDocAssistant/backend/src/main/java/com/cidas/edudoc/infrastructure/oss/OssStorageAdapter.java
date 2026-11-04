package com.cidas.edudoc.infrastructure.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.sts20150401.models.AssumeRoleRequest;
import com.aliyun.sts20150401.models.AssumeRoleResponse;
import com.aliyun.sts20150401.models.AssumeRoleResponseBody;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * OSS 适配：浏览器直传的短期 STS 签发（inline policy 收窄到单个 objectKey，概要 §5.4 不暴露长期凭据）。
 * 1103 审查 S6：SDK 异常原文（含 endpoint/RoleArn/RequestId）只进日志，客户端统一回固定文案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssStorageAdapter {

    private final OssProperties properties;
    private final ObjectProvider<com.aliyun.sts20150401.Client> stsProvider;
    private final ObjectProvider<OSS> ossProvider;

    /** 凭据缺失只影响 OSS 相关接口（50301 可读文案），不炸启动（1104 前端联调发现的设计缺陷）。 */
    private void requireConfigured() {
        if (properties.accessKeyId() == null || properties.accessKeyId().isBlank()) {
            throw new CidasBusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "OSS 未配置：请设置环境变量 EDUDOC_OSS_ACCESS_KEY_ID / EDUDOC_OSS_ACCESS_KEY_SECRET 后重启",
                    false, Map.of());
        }
    }

    private com.aliyun.sts20150401.Client stsClient() {
        requireConfigured();
        return stsProvider.getObject();
    }

    private OSS ossClient() {
        requireConfigured();
        return ossProvider.getObject();
    }

    public UploadCredentials issueUploadCredentials(String objectKey, String roleSessionName) {
        String policy = """
                {"Version":"1","Statement":[{"Effect":"Allow","Action":[
                "oss:PutObject","oss:InitiateMultipartUpload","oss:UploadPart",
                "oss:CompleteMultipartUpload","oss:AbortMultipartUpload","oss:ListParts"],
                "Resource":"acs:oss:*:*:%s/%s"}]}"""
                .formatted(properties.bucket(), objectKey);
        var stsClient = stsClient();
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
            log.warn("STS AssumeRole 失败 objectKey={}", objectKey, e);
            throw new CidasBusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "上传凭据签发失败，请稍后重试", true, Map.of());
        }
    }

    /** 对象头校验（complete 回调第一步）：不存在返回空，服务端不可用抛 50301。 */
    public Optional<ObjectInfo> headObject(String objectKey) {
        var ossClient = ossClient();
        try {
            ObjectMetadata meta = ossClient.getObjectMetadata(properties.bucket(), objectKey);
            return Optional.of(new ObjectInfo(meta.getContentLength(), meta.getETag(),
                    meta.getVersionId(), meta.getServerSideEncryption()));
        } catch (OSSException e) {
            if ("NoSuchKey".equals(e.getErrorCode())) {
                return Optional.empty();
            }
            log.warn("OSS headObject 失败 objectKey={} errorCode={}", objectKey, e.getErrorCode(), e);
            throw new CidasBusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "文件校验失败，请稍后重试", true, Map.of());
        } catch (Exception e) {
            log.warn("OSS headObject 异常 objectKey={}", objectKey, e);
            throw new CidasBusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "文件校验失败，请稍后重试", true, Map.of());
        }
    }

    /** 流式实算对象 SHA-256（小写 hex，20MB 上限内单机可接受）。 */
    public String sha256Of(String objectKey) {
        var ossClient = ossClient();
        try (OSSObject object = ossClient.getObject(properties.bucket(), objectKey);
             InputStream in = object.getObjectContent()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            log.warn("OSS sha256 读取失败 objectKey={}", objectKey, e);
            throw new CidasBusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "文件内容读取失败，请稍后重试", true, Map.of());
        }
    }

    /** 对象下载到本地临时文件（解析 worker 用，防 20MB 堆内膨胀）。 */
    public void downloadTo(String objectKey, java.nio.file.Path target) {
        var ossClient = ossClient();
        try {
            ossClient.getObject(new com.aliyun.oss.model.GetObjectRequest(properties.bucket(), objectKey),
                    target.toFile());
        } catch (Exception e) {
            log.warn("OSS 下载失败 objectKey={}", objectKey, e);
            throw new CidasBusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "文件下载失败，请稍后重试", true, Map.of());
        }
    }

    /** 浏览器直传所需的全部信息（STS 临时凭据 + 目标对象位置）。 */
    public record UploadCredentials(String accessKeyId, String accessKeySecret, String securityToken,
                                    String expiration, String bucket, String region, String endpoint) {
    }

    /** 对象头信息（size/ETag/版本/加密方式）。 */
    public record ObjectInfo(long contentLength, String etag, String versionId, String encryption) {
    }
}
