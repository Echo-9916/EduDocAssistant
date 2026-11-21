package com.cidas.edudoc.document.application;

/** 按当前版本文本 + 结构层生成 Word（模板样式优先，没有模板回落内置样式）。 */
public interface DocumentWordExportService {

    byte[] exportDocx(String documentId);

    /** 内部（worker）按指定版本生成 DOCX；调用方已完成权限校验，不再重复读权限。 */
    byte[] exportVersionDocx(String documentId, String versionId);
}