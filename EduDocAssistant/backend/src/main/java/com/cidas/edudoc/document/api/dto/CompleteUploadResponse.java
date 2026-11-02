package com.cidas.edudoc.document.api.dto;

import com.cidas.edudoc.document.application.UploadSessionService.CompletionResult;

/** 完成上传响应：新建文档与首个版本 ID（十六进制串）。 */
public record CompleteUploadResponse(String documentId, String versionId) {

    public static CompleteUploadResponse from(CompletionResult result) {
        return new CompleteUploadResponse(result.documentId(), result.versionId());
    }
}
