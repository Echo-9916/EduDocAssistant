package com.cidas.edudoc.document.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 文档版本持久化对象（edudoc_document_version，版本不可变）。 */
@Data
@AllArgsConstructor
public class DocumentVersion {

    private String id;
    private String documentId;
    private int versionNo;
    private String versionType;
    private String sourceObjectId;
    private String editorId;
    private String contentHash;
    private String versionStatus;
}
