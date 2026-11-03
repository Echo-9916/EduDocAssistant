package com.cidas.edudoc.document.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 文档建档与当前版本指针维护（SQL 委托给 {@link DocumentMapper}）。 */
@Repository
@RequiredArgsConstructor
public class DocumentRepository {

    private final DocumentMapper mapper;

    public void insert(Document document) {
        mapper.insert(document);
    }

    public void updateCurrentVersion(String documentId, String versionId) {
        mapper.updateCurrentVersion(documentId, versionId);
    }

    public Optional<Document> findById(String documentIdHex) {
        return Optional.ofNullable(mapper.selectById(documentIdHex));
    }

    public void updateStatus(String documentIdHex, String status) {
        mapper.updateStatus(documentIdHex, status);
    }

    public List<DocumentViewRow> page(String ownerId, String courseId, String docType, String status,
                                      String keyword, int offset, int limit) {
        return mapper.selectPage(ownerId, courseId, docType, status, keyword, offset, limit);
    }

    public long count(String ownerId, String courseId, String docType, String status, String keyword) {
        return mapper.countPage(ownerId, courseId, docType, status, keyword);
    }

    public Optional<DocumentViewRow> detail(String documentIdHex) {
        return Optional.ofNullable(mapper.selectDetail(documentIdHex));
    }

    /** 软删（回收站），已 DELETED 时 0 行。 */
    public int softDelete(String documentIdHex) {
        return mapper.softDelete(documentIdHex);
    }

    public int restore(String documentIdHex, String status) {
        return mapper.restore(documentIdHex, status);
    }
}
