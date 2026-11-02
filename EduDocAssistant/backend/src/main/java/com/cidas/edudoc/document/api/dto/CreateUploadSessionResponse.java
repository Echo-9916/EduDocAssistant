package com.cidas.edudoc.document.api.dto;

import com.cidas.edudoc.document.application.UploadSessionService.SessionTicket;
import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter.UploadCredentials;

/** 创建上传会话响应：会话 ID + objectKey + STS 临时凭据（含过期时间）+ 桶定位。 */
public record CreateUploadSessionResponse(String sessionId, String objectKey,
                                          String bucket, String region, String endpoint, Sts sts) {

    public record Sts(String accessKeyId, String accessKeySecret, String securityToken, String expiration) {
    }

    public static CreateUploadSessionResponse from(SessionTicket ticket) {
        UploadSession session = ticket.session();
        UploadCredentials credentials = ticket.credentials();
        return new CreateUploadSessionResponse(session.id(), session.objectKey(),
                credentials.bucket(), credentials.region(), credentials.endpoint(),
                new Sts(credentials.accessKeyId(), credentials.accessKeySecret(),
                        credentials.securityToken(), credentials.expiration()));
    }
}
