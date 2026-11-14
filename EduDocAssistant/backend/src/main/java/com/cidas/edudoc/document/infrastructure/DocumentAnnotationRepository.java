package com.cidas.edudoc.document.infrastructure;

import com.cidas.edudoc.common.persistence.Ids;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 文档批注读写（SQL 委托给 {@link DocumentAnnotationMapper}）。 */
@Repository
@RequiredArgsConstructor
public class DocumentAnnotationRepository {

    private final DocumentAnnotationMapper mapper;

    public String create(String documentIdHex, String versionIdHex, String authorIdHex,
                         int charStart, int charEnd, String content) {
        String idHex = Ids.hex16();
        mapper.insert(idHex, documentIdHex, versionIdHex, authorIdHex, charStart, charEnd, content);
        return idHex;
    }

    public List<DocumentAnnotationRow> listByDocument(String documentIdHex, String status) {
        return mapper.selectByDocument(documentIdHex, status);
    }

    public DocumentAnnotationRow findById(String annotationIdHex) {
        return mapper.selectById(annotationIdHex);
    }

    public int resolve(String annotationIdHex, String resolvedByHex) {
        return mapper.resolve(annotationIdHex, resolvedByHex);
    }

    public int reopen(String annotationIdHex) {
        return mapper.reopen(annotationIdHex);
    }

    public int delete(String annotationIdHex) {
        return mapper.delete(annotationIdHex);
    }
}