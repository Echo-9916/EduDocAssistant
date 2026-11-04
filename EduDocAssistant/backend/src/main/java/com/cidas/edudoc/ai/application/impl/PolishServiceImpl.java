package com.cidas.edudoc.ai.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.ai.application.AiGateway;
import com.cidas.edudoc.ai.application.PolishService;
import com.cidas.edudoc.ai.infrastructure.AiTask;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.VersionChunk;
import com.cidas.edudoc.ai.infrastructure.PolishResult;
import com.cidas.edudoc.ai.infrastructure.PolishResultRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 润色实现（C-07）。REVERT 口径（台账裁决）：表约束 ck_polish_decision 无 REVERT 值（零 DDL 前提），
 * 撤销 = 弹出当前 EDIT 版本、current 回拨 version_no-1、决策回写 PENDING（可重润/重采纳），
 * 历史由审计 POLISH_REVERTED 承载；D3「回拨至 document_version_id」在单次采纳链下与弹栈等价。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolishServiceImpl implements PolishService {

    static final int MAX_TOTAL_CHARS = 5000;

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final ChunkEmbeddingRepository embeddingRepository;
    private final AiGateway aiGateway;
    private final PolishResultRepository polishRepository;
    private final PolishTxWriter txWriter;
    private final OssStorageAdapter ossStorageAdapter;
    private final OssProperties ossProperties;
    private final AuditService auditService;

    @Override
    public List<PolishRowView> polish(String documentId, List<String> paragraphTexts) {
        UserPrincipal principal = AuthContext.require();
        Document doc = requireOwnedDocument(documentId, principal);
        if (!"READY".equals(doc.getStatus())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "文档尚未就绪");
        }
        if (paragraphTexts == null || paragraphTexts.isEmpty()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "请至少选择一个段落");
        }
        int total = 0;
        for (String text : paragraphTexts) {
            if (text == null || text.isBlank()) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "段落文本不能为空");
            }
            total += text.length();
        }
        if (total > MAX_TOTAL_CHARS) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST,
                    "选中段落合计不得超过 5000 字符（当前 " + total + "）");
        }
        List<String> texts = paragraphTexts.stream().map(String::strip).toList();
        AiGateway.PolishOutcome outcome = aiGateway.polish(texts); // 只送选中段落（§4.2）

        String taskId = Ids.hex16();
        AiTask task = new AiTask(taskId, doc.getId(), doc.getCurrentVersionId(), "POLISH",
                "SUCCEEDED", 5, null, "{\"source\":\"polish-sync\"}",
                principal.userId(), 100, null, null, 0);
        List<PolishResult> rows = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            rows.add(new PolishResult(Ids.hex16(), doc.getId(), doc.getCurrentVersionId(),
                    principal.userId(), taskId, texts.get(i), outcome.polished().get(i), null, "PENDING"));
        }
        txWriter.persistRows(task, rows);
        auditService.record("AI_TASK_SUCCEEDED", "AI_TASK", taskId, doc.getId(), "200",
                Map.of("taskType", "POLISH", "paragraphCount", texts.size(),
                        "model", outcome.model() == null ? "" : outcome.model(),
                        "promptVersion", outcome.promptVersion() == null ? "" : outcome.promptVersion()));
        return rows.stream()
                .map(r -> new PolishRowView(r.getId(), r.getSourceText(), r.getPolishedText(), r.getDecision()))
                .toList();
    }

    @Override
    public AppliedView apply(String polishId, String decision, String acceptedText) {
        UserPrincipal principal = AuthContext.require();
        PolishResult row = requireOwnRow(polishId, principal);
        if (!"PENDING".equals(row.getDecision())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "该润色结果已被处理");
        }
        if (!"ACCEPT".equals(decision) && !"PARTIAL".equals(decision)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "decision 仅支持 ACCEPT|PARTIAL");
        }
        String accepted = "ACCEPT".equals(decision) ? row.getPolishedText()
                : acceptedText == null ? "" : acceptedText.strip();
        if (accepted.isBlank()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "部分采纳必须回传采纳文本");
        }
        Document doc = requireOwnedDocument(row.getDocumentId(), principal);
        // 链式采纳基于最新版本重放替换（§4.2 并发冲突 40901）：当前版本全文按分块密铺重建
        DocumentVersion current = versionRepository.findById(doc.getCurrentVersionId())
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.CONFLICT, "当前版本缺失"));
        String docText = reconstructText(current.getId());
        int idx = docText.indexOf(row.getSourceText());
        if (idx < 0) {
            throw new CidasBusinessException(ErrorCode.CONFLICT,
                    "目标段落不在最新版本中（文档已被更新），请重新润色");
        }
        String newText = docText.substring(0, idx) + accepted + docText.substring(idx + row.getSourceText().length());

        String objectKey = ossProperties.objectKeyPrefix() + "/derived/" + doc.getId()
                + "/" + Ids.hex16() + ".txt";
        OssStorageAdapter.ObjectInfo info = ossStorageAdapter.putText(objectKey, newText); // 网络写入在事务外
        OssObject derived = new OssObject(Ids.hex16(), ossProperties.bucket(), ossProperties.region(),
                objectKey, info.versionId(), info.etag(), sha256Hex(newText), info.contentLength(),
                "text/plain", info.encryption(), "DERIVED");
        AppliedView view = txWriter.applyVersion(derived, doc, principal.userId(), newText,
                row.getId(), decision, accepted);
        auditService.record("POLISH_APPLIED", "DOCUMENT", doc.getId(), doc.getId(), "200",
                Map.of("polishId", row.getId(), "decision", decision,
                        "versionId", view.versionId(), "versionNo", view.versionNo()));
        return view;
    }

    @Override
    public void reject(String polishId) {
        UserPrincipal principal = AuthContext.require();
        requireOwnRow(polishId, principal);
        if (!polishRepository.updateDecisionIfPending(polishId, "REJECT", null)) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "该润色结果已被处理");
        }
        auditService.record("POLISH_REJECTED", "POLISH_RESULT", polishId, polishId, "200", Map.of());
    }

    @Override
    public void revert(String polishId) {
        UserPrincipal principal = AuthContext.require();
        PolishResult row = requireOwnRow(polishId, principal);
        if (!"ACCEPT".equals(row.getDecision()) && !"PARTIAL".equals(row.getDecision())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "该结果不是未撤销的采纳");
        }
        PolishResult latest = polishRepository.findLatestApplied(row.getDocumentId())
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.CONFLICT, "无可撤销的采纳记录"));
        if (!latest.getId().equals(row.getId())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "仅可撤销最近一次采纳");
        }
        Document doc = requireOwnedDocument(row.getDocumentId(), principal);
        DocumentVersion current = versionRepository.findById(doc.getCurrentVersionId())
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.CONFLICT, "当前版本缺失"));
        if (!"EDIT".equals(current.getVersionType())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "当前版本不是润色采纳版本，不可撤销");
        }
        DocumentVersion previous = versionRepository
                .findByDocumentAndVersionNo(doc.getId(), current.getVersionNo() - 1)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.CONFLICT, "前一版本缺失"));
        txWriter.revertToPrevious(doc, previous.getId(), row.getId());
        auditService.record("POLISH_REVERTED", "DOCUMENT", doc.getId(), doc.getId(), "200",
                Map.of("polishId", row.getId(), "restoredVersionId", previous.getId()));
    }

    // ---------- 内部 ----------

    String reconstructText(String versionId) {
        List<VersionChunk> chunks = embeddingRepository.selectVersionChunks(versionId);
        if (chunks.isEmpty()) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "当前版本无分块，无法采纳润色");
        }
        StringBuilder sb = new StringBuilder();
        chunks.forEach(c -> sb.append(c.contentText() == null ? "" : c.contentText()));
        return sb.toString();
    }

    private PolishResult requireOwnRow(String polishId, UserPrincipal principal) {
        if (!Ids.isHex16(polishId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND);
        }
        PolishResult row = polishRepository.findById(polishId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND));
        if (!principal.userId().equalsIgnoreCase(row.getUserId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "润色结果不存在或不可见");
        }
        return row;
    }

    private Document requireOwnedDocument(String documentId, UserPrincipal principal) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND);
        }
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND));
        if (!principal.userId().equalsIgnoreCase(doc.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在或不可见");
        }
        return doc;
    }

    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
