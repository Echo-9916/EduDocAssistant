package com.cidas.edudoc.document.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 文档分块持久化对象（edudoc_document_chunk）。 */
@Data
@AllArgsConstructor
public class DocumentChunk {

    private String id;
    private String documentVersionId;
    private int chunkNo;
    private String headingPath;
    private Integer pageNo;
    private int charStart;
    private int charEnd;
    private String contentText;
    private int tokenCount;
    private String contentHash;
    private String indexStatus;
}
