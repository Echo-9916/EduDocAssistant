package com.cidas.edudoc.document.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
}
