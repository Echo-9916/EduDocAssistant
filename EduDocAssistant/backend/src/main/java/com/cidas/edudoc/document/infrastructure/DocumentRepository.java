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

    /** 按模板新建时绑定模板版本（D-07/UT-13：绑定后不随模板新发布前移）。 */
    public void updateTemplateVersion(String documentId, String templateVersionId) {
        mapper.updateTemplateVersion(documentId, templateVersionId);
    }

    /** 修复成版并发守卫（终审 I1）：返回 1=前移成功，0=基版本已被并发推进，调用方回滚 40901。 */
    public int updateCurrentVersionCas(String documentId, String expectedVersionId, String newVersionId) {
        return mapper.updateCurrentVersionCas(documentId, expectedVersionId, newVersionId);
    }

    public Optional<Document> findById(String documentIdHex) {
        return Optional.ofNullable(mapper.selectById(documentIdHex));
    }

    /** 在调用方事务内锁定文档行；锁随事务提交/回滚释放。 */
    public void lockForUpdate(String documentIdHex) {
        mapper.selectForUpdate(documentIdHex);
    }

    public void updateStatus(String documentIdHex, String status) {
        mapper.updateStatus(documentIdHex, status);
    }

    /** 返回 1 表示迁移成功；0 表示文档已不在 PARSING（迟到/重复事件），调用方跳过回写。 */
    public int markStatusIfParsing(String documentIdHex, String status) {
        return mapper.updateStatusFromParsing(documentIdHex, status);
    }

    public List<DocumentViewRow> page(String ownerId, String courseId, String docType, String status,
                                      String keyword, long offset, int limit) {
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
