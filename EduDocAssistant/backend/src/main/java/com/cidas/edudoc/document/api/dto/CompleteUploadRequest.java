package com.cidas.edudoc.document.api.dto;

import java.util.List;

/** 完成上传回调（详细设计 §3.1.3）：contentHash 为浏览器实算 SHA-256 hex；parts 仅透传留存，服务端以实算为准。 */
public record CompleteUploadRequest(String etag, String contentHash, List<Part> parts) {

    public record Part(int partNumber, String etag) {
    }
}
