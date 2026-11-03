package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.document.application.DocumentParseTxService;
import com.cidas.edudoc.document.domain.Chunk;
import com.cidas.edudoc.document.infrastructure.DocumentChunk;
import com.cidas.edudoc.document.infrastructure.DocumentChunkRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentParseTxServiceImpl implements DocumentParseTxService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public void writeSuccess(String documentId, String versionId, List<Chunk> chunks) {
        chunkRepository.deleteByVersion(versionId);
        chunkRepository.insertAll(chunks.stream()
                .map(chunk -> new DocumentChunk(Ids.hex16(), versionId, chunk.chunkNo(),
                        chunk.headingPath(), chunk.pageNo(), chunk.charStart(), chunk.charEnd(),
                        chunk.text(), chunk.tokenCount(), chunk.contentHash(), "PENDING"))
                .toList());
        documentRepository.updateStatus(documentId, "READY");
        auditService.record("PARSE_SUCCEEDED", "DOCUMENT", documentId, documentId, "200",
                Map.of("versionId", versionId, "chunkCount", chunks.size()));
    }

    @Override
    @Transactional
    public void writeFailure(String documentId, String versionId, String reason) {
        documentRepository.updateStatus(documentId, "FAILED");
        auditService.record("PARSE_FAILED", "DOCUMENT", documentId, documentId, "42201",
                Map.of("versionId", versionId, "reason", reason == null ? "" : trim(reason)));
    }

    private static String trim(String value) {
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
