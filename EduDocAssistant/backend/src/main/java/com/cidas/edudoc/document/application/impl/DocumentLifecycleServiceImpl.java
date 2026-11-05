package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.document.application.DocumentLifecycleService;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentChunkRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DocumentLifecycleServiceImpl implements DocumentLifecycleService {

    private static final Set<String> REPARSABLE_STATUSES = Set.of("READY", "FAILED");

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final OssObjectRepository ossObjectRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public void delete(String documentId) {
        Document doc = requireOwned(documentId);
        if ("DELETED".equals(doc.getStatus())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "文档已在回收站");
        }
        documentRepository.softDelete(doc.getId());
        auditService.record("DOCUMENT_DELETED", "DOCUMENT", doc.getId(), doc.getId(), "200",
                Map.of("prevStatus", doc.getStatus()));
    }

    @Override
    @Transactional
    public void restore(String documentId) {
        Document doc = requireOwned(documentId);
        if (!"DELETED".equals(doc.getStatus())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "文档不在回收站");
        }
        // 恢复状态按当前版本是否已有分块推断（prevStatus 从审计回读太绕，不用）
        int chunks = doc.getCurrentVersionId() == null ? 0
                : chunkRepository.countByVersion(doc.getCurrentVersionId());
        String restoredStatus = chunks > 0 ? "READY" : "FAILED";
        try {
            if (documentRepository.restore(documentId, restoredStatus) == 0) {
                throw new CidasBusinessException(ErrorCode.CONFLICT, "文档状态已变化，请刷新后重试");
            }
        } catch (DuplicateKeyException e) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "已存在同名活跃文档，无法恢复");
        }
        auditService.record("DOCUMENT_RESTORED", "DOCUMENT", doc.getId(), doc.getId(), "200",
                Map.of("restoredStatus", restoredStatus));
    }

    @Override
    @Transactional
    public String reparse(String documentId, String versionId) {
        Document doc = requireOwned(documentId);
        if (!REPARSABLE_STATUSES.contains(doc.getStatus())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "当前状态不允许重新解析");
        }
        String targetVersion = versionId == null || versionId.isBlank()
                ? doc.getCurrentVersionId() : versionId;
        if (!targetVersion.equalsIgnoreCase(doc.getCurrentVersionId())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "仅可重新解析当前版本");
        }
        var sourceObject = documentVersionRepository.findById(targetVersion)
                .flatMap(version -> ossObjectRepository.findById(version.getSourceObjectId()))
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.CONFLICT, "版本源对象缺失"));
        documentRepository.updateStatus(documentId, "PARSING");
        // 1105 冒烟缺口：EDIT 版本源对象是服务端派生 .txt（text/plain），不能走 Tika 伪装防御，
        // 标记 textMode 由 worker 直接按「文本即结构」简化解析（C-07 口径）
        boolean textMode = "text/plain".equals(sourceObject.getMimeType())
                || "PARSED".equals(sourceObject.getObjectRole());
        String taskId = outboxEventRepository.publish("DOCUMENT_VERSION", targetVersion,
                "DOCUMENT_PARSE_REQUESTED", Map.of(
                        "schemaVersion", 1,
                        "documentId", documentId,
                        "versionId", targetVersion,
                        "bucket", sourceObject.getBucketName(),
                        "objectKey", sourceObject.getObjectKey(),
                        "textMode", textMode));
        auditService.record("PARSE_REQUESTED", "DOCUMENT", documentId, documentId, "200",
                Map.of("versionId", targetVersion, "taskId", taskId));
        return taskId;
    }

    /** owner 校验：非本人或不存在一律 40401，与详情一致不泄露存在性。 */
    private Document requireOwned(String documentId) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档 ID 格式不合法");
        }
        String ownerId = AuthContext.require().userId();
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        if (!ownerId.equalsIgnoreCase(doc.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }
}
