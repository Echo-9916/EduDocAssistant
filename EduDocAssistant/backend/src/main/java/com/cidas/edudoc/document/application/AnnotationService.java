package com.cidas.edudoc.document.application;

import java.time.OffsetDateTime;
import java.util.List;

/** 文档批注：owner 与协作者可读、可批注；仅作者或 owner 可删除。 */
public interface AnnotationService {

    List<AnnotationView> list(String documentId, String status);

    AnnotationView create(String documentId, CreateCommand command);

    AnnotationView resolve(String annotationId, boolean resolved);

    void delete(String annotationId);

    /** charStart/charEnd 为 chunk 闭区间原文偏移；versionId 缺省取当前版本。 */
    record CreateCommand(String versionId, Integer charStart, Integer charEnd, String content) {
    }

    /** canDelete 仅作者；canResolve 为文档 owner 或作者（其余协作者不可解决/重开）。 */
    record AnnotationView(String id, String documentId, String documentVersionId,
                          String authorId, String authorName, String authorAccount,
                          int charStart, int charEnd, String content, String status,
                          String resolvedBy, String resolvedByName, OffsetDateTime resolvedTime,
                          OffsetDateTime createTime, boolean canDelete, boolean canResolve) {
    }
}