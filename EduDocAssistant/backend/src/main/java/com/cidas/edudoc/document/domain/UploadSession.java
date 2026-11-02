package com.cidas.edudoc.document.domain;

import java.time.Instant;

/** 上传会话（Redis 态，无数据库表；概要 §2.2「Redis 只保存可重建的会话」）。 */
public record UploadSession(String id, String ownerUserId, String courseId, String title,
                            DocumentType documentType, String fileName, long sizeBytes, String mimeType,
                            String objectKey, Status status, String completedDocumentId,
                            String completedVersionId, Instant createdAt) {

    /** OPEN 可 complete；COMPLETING 正在建档（并发拒绝）；COMPLETED 已建档可重放。 */
    public enum Status { OPEN, COMPLETING, COMPLETED }

    public UploadSession withStatus(Status next) {
        return new UploadSession(id, ownerUserId, courseId, title, documentType, fileName, sizeBytes,
                mimeType, objectKey, next, completedDocumentId, completedVersionId, createdAt);
    }

    public UploadSession completed(String documentId, String versionId) {
        return new UploadSession(id, ownerUserId, courseId, title, documentType, fileName, sizeBytes,
                mimeType, objectKey, Status.COMPLETED, documentId, versionId, createdAt);
    }
}
