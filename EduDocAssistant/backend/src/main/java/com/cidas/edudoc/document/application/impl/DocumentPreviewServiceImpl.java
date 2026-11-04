package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.document.application.DocumentPreviewService;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentChunkRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 预览只读：owner 口径同 02 详情（非本人 40401 不泄露存在性）；versionId 仅允许当前版本（40901）。 */
@Service
@RequiredArgsConstructor
public class DocumentPreviewServiceImpl implements DocumentPreviewService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentChunkRepository chunkRepository;

    @Override
    public Preview preview(String documentId, String versionId) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND);
        }
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND));
        if (!AuthContext.require().userId().equalsIgnoreCase(doc.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在或不可见");
        }
        String targetVersion = versionId == null || versionId.isBlank()
                ? doc.getCurrentVersionId() : versionId;
        if (!targetVersion.equalsIgnoreCase(doc.getCurrentVersionId())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "仅可预览当前版本");
        }
        DocumentVersion version = versionRepository.findById(targetVersion)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.CONFLICT, "版本缺失"));
        return new Preview(doc.getId(), version.getId(), version.getVersionNo(),
                chunkRepository.selectAllByVersion(targetVersion).stream()
                        .map(c -> new Block(c.getId(), c.getChunkNo(), c.getHeadingPath(), c.getPageNo(),
                                c.getCharStart(), c.getCharEnd(), c.getContentText()))
                        .toList());
    }
}
