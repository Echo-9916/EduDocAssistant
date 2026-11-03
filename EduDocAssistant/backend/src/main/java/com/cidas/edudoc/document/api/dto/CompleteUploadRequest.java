package com.cidas.edudoc.document.api.dto;

import java.util.List;

/**
 * 完成上传回调（详细设计 §3.1.3）：contentHash 为浏览器实算 SHA-256 hex。
 * 1103 审查 S8：parts 服务端接收但<b>不校验、不落库</b>——内容完整性由服务端实算 SHA-256 兜底（B-04 ③），
 * 断点续传的 checkpoint 由浏览器侧自持（B-10）。前端接线不要依赖此处回读分片清单。
 */
public record CompleteUploadRequest(String etag, String contentHash, List<Part> parts) {

    public record Part(int partNumber, String etag) {
    }
}
