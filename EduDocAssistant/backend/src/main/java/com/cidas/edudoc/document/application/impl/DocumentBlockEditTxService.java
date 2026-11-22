package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.document.application.DocumentBlockEditService.Result;
import com.cidas.edudoc.document.application.DocumentParseTxService;
import com.cidas.edudoc.document.domain.ChunkingResult;
import com.cidas.edudoc.document.domain.StructureNode;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** 块编辑成版事务：派生对象登记 → EDIT 版本 → current/状态 → 分块+结构+embed → 审计，单事务。 */
@Component
@RequiredArgsConstructor
public class DocumentBlockEditTxService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final OssObjectRepository ossObjectRepository;
    private final DocumentParseTxService parseTxService;
    private final AuditService auditService;

    @Transactional
    public Result save(Document doc, OssObject derived, String editorId, String newText,
                       ChunkingResult chunking, int blockCount, List<StructureNode> nodes) {
        int versionNo = versionRepository.maxVersionNo(doc.getId()) + 1;
        String versionId = Ids.hex16();
        ossObjectRepository.insert(derived);
        try {
            versionRepository.insert(new DocumentVersion(versionId, doc.getId(), versionNo, "EDIT",
                    derived.getId(), editorId, derived.getContentHash(), "A"));
        } catch (DuplicateKeyException e) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "并发编辑冲突，请基于最新版本重试");
        }
        documentRepository.updateCurrentVersion(doc.getId(), versionId);
        documentRepository.updateStatus(doc.getId(), "PARSING");
        if (!parseTxService.writeSuccess(doc.getId(), versionId, chunking.chunks(), nodes)) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "文档状态已变化，请刷新后重试");
        }
        auditService.record("DOCUMENT_BLOCK_EDITED", "DOCUMENT", doc.getId(), doc.getId(), "200",
                Map.of("versionId", versionId, "versionNo", versionNo, "blockCount", blockCount,
                        "textLength", newText.length()));
        return new Result(versionId, versionNo);
    }
}