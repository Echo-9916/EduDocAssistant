package com.cidas.edudoc.document.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 文档内图片（edudoc_document_image）：正文流之外，按结构节点 anchor 定位。 */
@Data
@AllArgsConstructor
public class DocumentImage {

    private String id;
    private String documentId;
    private String uploaderId;
    private String fileName;
    private String mimeType;
    private long sizeBytes;
    private String objectKey;
}