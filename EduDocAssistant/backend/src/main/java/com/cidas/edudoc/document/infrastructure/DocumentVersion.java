package com.cidas.edudoc.document.infrastructure;

import lombok.Data;

/** 文档版本持久化对象（edudoc_document_version，版本内容不可变）。 */
@Data
public class DocumentVersion {

    private String id;
    private String documentId;
    private int versionNo;
    private String versionType;
    private String sourceObjectId;
    private String editorId;
    private String contentHash;
    private String versionStatus;
    /** 解析派生元数据（阅读结构层 JSON），不是版本内容。 */
    private String structureJson;

    public DocumentVersion() {
    }

    /** 既有 8 参构造保留，MyBatis 走无参 + setter。 */
    public DocumentVersion(String id, String documentId, int versionNo, String versionType,
                           String sourceObjectId, String editorId, String contentHash, String versionStatus) {
        this.id = id;
        this.documentId = documentId;
        this.versionNo = versionNo;
        this.versionType = versionType;
        this.sourceObjectId = sourceObjectId;
        this.editorId = editorId;
        this.contentHash = contentHash;
        this.versionStatus = versionStatus;
    }
}