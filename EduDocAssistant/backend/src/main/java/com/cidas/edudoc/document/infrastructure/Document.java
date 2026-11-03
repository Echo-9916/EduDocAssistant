package com.cidas.edudoc.document.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 文档持久化对象（edudoc_document，id 等 RAW(16) 转 hex 字符串；courseId 可空）。 */
@Data
@AllArgsConstructor
public class Document {

    private String id;
    private String courseId;
    private String ownerId;
    private String title;
    private String docType;
    private String status;
    private String currentVersionId;
}
