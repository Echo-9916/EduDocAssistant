package com.cidas.edudoc.document.api.dto;

/** 创建上传会话请求（详细设计 §3.1.3）：教师端 courseId 必填，学生端可空。 */
public record CreateUploadSessionRequest(String courseId, String title, String fileName,
                                         long size, String mimeType, String docType) {
}
