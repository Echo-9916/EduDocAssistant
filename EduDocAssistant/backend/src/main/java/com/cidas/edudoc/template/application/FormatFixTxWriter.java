package com.cidas.edudoc.template.application;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.template.infrastructure.FormatCheck;
import com.cidas.edudoc.template.infrastructure.FormatCheckRepository;
import com.cidas.edudoc.template.infrastructure.FormatIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 修复成版写事务（D-05）：新 docx 对象（role=ORIGINAL，每版本一原始对象口径）→ EDIT 版本 →
 * current CAS 前移（终审 I1：expected=被校验版本，并发双修复必有一方 40901 回滚，杜绝同基版双 EDIT）
 * → 重解析事件 → 问题行 FIXED → 复审任务 QUEUED + 事件；审计同事务。
 */
@Component
@RequiredArgsConstructor
public class FormatFixTxWriter {

    private final OssObjectRepository ossObjectRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentRepository documentRepository;
    private final FormatCheckRepository checkRepository;
    private final FormatIssueRepository issueRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditService auditService;

    public record FixRequest(OssObject object, DocumentVersion version, List<String> fixedIssueIds,
                             FormatCheck recheck, String sourceCheckId, String sourceVersionId) {
    }

    public record FixOutcome(String versionId, int versionNo, String recheckId) {
    }

    @Transactional
    public FixOutcome apply(FixRequest request) {
        OssObject object = request.object();
        DocumentVersion version = request.version();
        ossObjectRepository.insert(object);
        try {
            versionRepository.insert(version);
        } catch (DuplicateKeyException e) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "并发生成版本冲突，请基于最新版本重试");
        }
        if (documentRepository.updateCurrentVersionCas(version.getDocumentId(),
                request.sourceVersionId(), version.getId()) == 0) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "文档已被并发修改，修复回滚；请重新发起校验");
        }
        documentRepository.updateStatus(version.getDocumentId(), "PARSING");
        outboxEventRepository.publish("DOCUMENT_VERSION", version.getId(), "DOCUMENT_PARSE_REQUESTED", Map.of(
                "schemaVersion", 1,
                "documentId", version.getDocumentId(),
                "versionId", version.getId(),
                "bucket", object.getBucketName(),
                "objectKey", object.getObjectKey()));
        issueRepository.markFixed(request.fixedIssueIds());
        recheckToNewVersion(request.recheck(), version);
        String recheckId = checkRepository.insert(request.recheck());
        outboxEventRepository.publish("FORMAT_CHECK", recheckId, "FORMAT_CHECK_REQUESTED", Map.of(
                "schemaVersion", 1,
                "checkId", recheckId,
                "documentId", version.getDocumentId(),
                "versionId", version.getId(),
                "templateVersionId", request.recheck().getTemplateVersionId(),
                "sourceObjectId", object.getId()));
        auditService.record("FORMAT_FIX_APPLIED", "DOCUMENT", version.getDocumentId(),
                version.getDocumentId(), "200",
                Map.of("sourceCheckId", request.sourceCheckId(),
                        "versionId", version.getId(), "versionNo", version.getVersionNo(),
                        "fixedIssueCount", request.fixedIssueIds().size(),
                        "recheckId", recheckId));
        return new FixOutcome(version.getId(), version.getVersionNo(), recheckId);
    }

    private static void recheckToNewVersion(FormatCheck recheck, DocumentVersion version) {
        recheck.setDocumentVersionId(version.getId());
    }
}
