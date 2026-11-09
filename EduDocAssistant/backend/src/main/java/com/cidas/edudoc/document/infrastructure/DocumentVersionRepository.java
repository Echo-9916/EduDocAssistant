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

    /** 当前最大版本号（无版本返回 0）。 */
    public int maxVersionNo(String documentIdHex) {
        Integer max = mapper.selectMaxVersionNo(documentIdHex);
        return max == null ? 0 : max;
    }

    public Optional<DocumentVersion> findByDocumentAndVersionNo(String documentIdHex, int versionNo) {
        return Optional.ofNullable(mapper.selectByDocumentAndVersionNo(documentIdHex, versionNo));
    }

    /** 验收决议④：版本历史（版本号降序）。 */
    public java.util.List<DocumentVersionRow> listByDocument(String documentIdHex) {
        return mapper.selectListByDocument(documentIdHex);
    }
}
