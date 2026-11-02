package com.cidas.edudoc.document.application;

import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter.UploadCredentials;

/** 上传会话编排：校验 → 生成 objectKey → 签发 STS → 落 Redis 会话。 */
public interface UploadSessionService {

    SessionTicket createSession(NewUpload upload);

    record NewUpload(String courseId, String title, String fileName, long sizeBytes,
                     String mimeType, String documentType) {
    }

    record SessionTicket(UploadSession session, UploadCredentials credentials) {
    }
}
