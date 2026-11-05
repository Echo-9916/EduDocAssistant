package com.cidas.edudoc.ai.application.impl;

import com.cidas.edudoc.ai.application.PolishService.AppliedView;
import com.cidas.edudoc.ai.infrastructure.AiTask;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.ai.infrastructure.PolishResult;
import com.cidas.edudoc.ai.infrastructure.PolishResultRepository;
import com.cidas.edudoc.document.application.DocumentParseTxService;
import com.cidas.edudoc.document.domain.Chunk;
import com.cidas.edudoc.document.domain.ChunkingService;
import com.cidas.edudoc.document.domain.TextBlock;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.document.infrastructure.ParseProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 润色写事务（C-07）：行落库与「成版五写」单事务提交——
 * 派生文本对象登记（角色取字典值 PARSED，ck_oss_role 无 DERIVED）→ EDIT 版本 → current 前移 → PARSING → 简化解析（文本即结构，跳 Tika）
 * 走 DocumentParseTxService 复用分块+向量化通道（DOCUMENT_EMBED_REQUESTED 自然衔接）。
 */
@Component
@RequiredArgsConstructor
public class PolishTxWriter {

    private final AiTaskRepository aiTaskRepository;
    private final PolishResultRepository polishRepository;
    private final OssObjectRepository ossObjectRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentParseTxService parseTxService;
    private final ParseProperties parseProperties;

    private final com.cidas.edudoc.access.application.AuditService auditService;

    /** 对照行落库 + AI_TASK_SUCCEEDED 审计同事务（1105 审查 P1-2：业务生效与审计不分离）。 */
    @Transactional
    public void persistRows(AiTask task, List<PolishResult> rows, String model, String promptVersion) {
        aiTaskRepository.insertOrReuse(task);
        polishRepository.insertBatch(rows);
        auditService.record("AI_TASK_SUCCEEDED", "AI_TASK", task.getId(), task.getDocumentId(), "200",
                java.util.Map.of("taskType", "POLISH", "paragraphCount", rows.size(),
                        "model", model == null ? "" : model,
                        "promptVersion", promptVersion == null ? "" : promptVersion));
    }

    /** 撤销（D3/台账裁决）：current 回拨前一版本、决策回写 PENDING（可重润/重采纳），版本行全部保留；审计同事务。 */
    @Transactional
    public void revertToPrevious(Document doc, String previousVersionId, String polishId) {
        documentRepository.updateCurrentVersion(doc.getId(), previousVersionId);
        polishRepository.updateDecision(polishId, "PENDING", null);
        auditService.record("POLISH_REVERTED", "DOCUMENT", doc.getId(), doc.getId(), "200",
                java.util.Map.of("polishId", polishId, "restoredVersionId", previousVersionId));
    }

    /** @param derivedObject 已写入 OSS 的派生对象登记行（网络写入在事务外完成） */
    @Transactional
    public AppliedView applyVersion(OssObject derivedObject, Document doc, String editorId,
                                    String newText, String polishId, String decision, String acceptedText) {
        int versionNo = versionRepository.maxVersionNo(doc.getId()) + 1;
        String versionId = Ids.hex16();
        ossObjectRepository.insert(derivedObject);
        try {
            versionRepository.insert(new DocumentVersion(versionId, doc.getId(), versionNo, "EDIT",
                    derivedObject.getId(), editorId, derivedObject.getContentHash(), "A"));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 审查修复（发现 2）：两条润色行并发成版撞 uk(document_id, version_no) → 40901 可重试，而非 50001
            throw new CidasBusinessException(ErrorCode.CONFLICT, "并发采纳冲突，请基于最新版本重试");
        }
        documentRepository.updateCurrentVersion(doc.getId(), versionId);
        documentRepository.updateStatus(doc.getId(), "PARSING");
        if (!polishRepository.updateDecisionIfPending(polishId, decision, acceptedText)) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "该润色结果已被处理");
        }
        // 简化解析：段落即结构（按行成块），跳过 Tika；成功后同事务补发向量化事件
        List<TextBlock> blocks = newText.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .map(line -> new TextBlock(line, null, 0))
                .toList();
        List<Chunk> chunks = new ChunkingService(parseProperties.chunkSize()).chunk(blocks);
        // 审查修复 P2-4：同事务写 Success 即 READY，响应回真实状态而非硬编码 PARSING
        boolean parsed = parseTxService.writeSuccess(doc.getId(), versionId, chunks);
        auditService.record("POLISH_APPLIED", "DOCUMENT", doc.getId(), doc.getId(), "200",
                java.util.Map.of("polishId", polishId, "decision", decision,
                        "versionId", versionId, "versionNo", versionNo));
        return new AppliedView(polishId, doc.getId(), versionId, versionNo, parsed ? "READY" : "PARSING");
    }
}
