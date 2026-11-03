package com.cidas.edudoc.document.api.dto;

/** 重新解析请求：versionId 缺省为文档当前版本。 */
public record ParseRequest(String versionId) {
}
