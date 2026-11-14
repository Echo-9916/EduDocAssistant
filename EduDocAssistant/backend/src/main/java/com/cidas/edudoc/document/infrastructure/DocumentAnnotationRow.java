package com.cidas.edudoc.document.infrastructure;

import lombok.Data;

import java.time.OffsetDateTime;

/** 批注行（annotation + 作者/解决人展示投影）。 */
@Data
public class DocumentAnnotationRow {

    private String id;
    private String documentId;
    private String documentVersionId;
    private String authorId;
    private String authorName;
    private String authorAccount;
    private int charStart;
    private int charEnd;
    private String content;
    private String status;
    private String resolvedBy;
    private String resolvedByName;
    private OffsetDateTime resolvedTime;
    private OffsetDateTime createTime;
}