package com.cidas.edudoc.document.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 文档版本读写（SQL 委托给 {@link DocumentVersionMapper}）。 */
@Repository
@RequiredArgsConstructor
public class DocumentVersionRepository {

    private final DocumentVersionMapper mapper;

    public void insert(DocumentVersion version) {
        mapper.insert(version);
    }

    public Optional<DocumentVersion> findBySourceObject(String sourceObjectIdHex) {
        return Optional.ofNullable(mapper.selectBySourceObject(sourceObjectIdHex));
    }

    public Optional<DocumentVersion> findById(String idHex) {
        return Optional.ofNullable(mapper.selectById(idHex));
    }
}
