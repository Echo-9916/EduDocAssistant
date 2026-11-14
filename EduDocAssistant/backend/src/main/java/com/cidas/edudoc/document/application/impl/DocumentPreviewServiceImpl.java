package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.document.application.CollaborationService;
import com.cidas.edudoc.document.application.DocumentPreviewService;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentChunkRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 预览只读：owner 口径同 02 详情（非本人 40401 不泄露存在性）。
 * versionId 允许文档内任意历史版本：版本抽屉的文本对比直接复用本端点取块文本。
 */
@Service
@RequiredArgsConstructor
public class DocumentPreviewServiceImpl implements DocumentPreviewService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentChunkRepository chunkRepository;
    private final CollaborationService collaborationService;

    @Override
    public Preview preview(String documentId, String versionId) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND);
        }
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND));
        String userId = AuthContext.require().userId();
        if (!userId.equalsIgnoreCase(doc.getOwnerId()) && !collaborationService.isCollaborator(documentId, userId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在或不可见");
        }
        String targetVersion = versionId == null || versionId.isBlank()
                ? doc.getCurrentVersionId() : versionId;
        // 版本必须属于本文档：跨文档 versionId 视为不存在（40401），不泄露其他文档版本
        DocumentVersion version = versionRepository.findById(targetVersion)
                .filter(v -> v.getDocumentId().equalsIgnoreCase(documentId))
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "版本不存在"));
        var chunks = chunkRepository.selectAllByVersion(targetVersion);
        // R3/1106 P2-1：前端据此区分「文本 READY」与「索引 READY」
        boolean vectorReady = !chunks.isEmpty()
                && chunks.stream().allMatch(c -> "READY".equals(c.getIndexStatus()));
        return new Preview(doc.getId(), version.getId(), version.getVersionNo(),
                chunks.stream()
                        .map(c -> new Block(c.getId(), c.getChunkNo(), c.getHeadingPath(), c.getPageNo(),
                                c.getCharStart(), c.getCharEnd(), c.getContentText()))
                        .toList(),
                vectorReady);
    }
}
