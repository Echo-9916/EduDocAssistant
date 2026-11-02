package com.cidas.edudoc.document.application;

import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter.UploadCredentials;

/** 上传会话编排：校验 → 生成 objectKey → 签发 STS → 落 Redis 会话。 */
public interface UploadSessionService {

    SessionTicket createSession(NewUpload upload);

    CompletionResult completeUpload(String sessionId, CompleteCommand command);

    record NewUpload(String courseId, String title, String fileName, long sizeBytes,
                     String mimeType, String documentType) {
    }

    /** parts 清单为前端 multipart checkpoint 摘要，v1 服务端以实算 SHA-256 为准，仅透传留存。 */
    record CompleteCommand(String etag, String contentHash) {
    }

    record CompletionResult(String documentId, String versionId) {
    }

    record SessionTicket(UploadSession session, UploadCredentials credentials) {
    }
}
