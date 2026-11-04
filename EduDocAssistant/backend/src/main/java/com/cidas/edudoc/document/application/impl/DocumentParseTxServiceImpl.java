package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.ai.application.EmbeddingCleanupService;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.document.application.DocumentParseTxService;
import com.cidas.edudoc.document.domain.Chunk;
import com.cidas.edudoc.document.infrastructure.DocumentChunk;
import com.cidas.edudoc.document.infrastructure.DocumentChunkRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParseTxServiceImpl implements DocumentParseTxService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final AuditService auditService;
    private final OutboxEventRepository outboxEventRepository;
    private final EmbeddingCleanupService embeddingCleanupService;

    @Override
    @Transactional
    public boolean writeSuccess(String documentId, String versionId, List<Chunk> chunks) {
        // 1103 审查 P5：状态守卫先行——文档不在 PARSING 即整笔跳过，不动分块不留假审计
        if (documentRepository.markStatusIfParsing(documentId, "READY") == 0) {
            return false;
        }
        // C-03：重解析换块前同事务清旧向量行（fk 无级联，旧向量随旧块物理删除——见任务台账裁决）
        embeddingCleanupService.deleteByVersion(versionId);
        chunkRepository.deleteByVersion(versionId);
        chunkRepository.insertAll(chunks.stream()
                .map(chunk -> new DocumentChunk(Ids.hex16(), versionId, chunk.chunkNo(),
                        chunk.headingPath(), chunk.pageNo(), chunk.charStart(), chunk.charEnd(),
                        chunk.text(), chunk.tokenCount(), chunk.contentHash(), "PENDING"))
                .toList());
        outboxEventRepository.publish("DOCUMENT_VERSION", versionId, "DOCUMENT_EMBED_REQUESTED",
                Map.of("schemaVersion", 1, "documentId", documentId, "versionId", versionId));
        auditService.record("PARSE_SUCCEEDED", "DOCUMENT", documentId, documentId, "200",
                Map.of("versionId", versionId, "chunkCount", chunks.size()));
        return true;
    }

    @Override
    @Transactional
    public boolean writeFailure(String documentId, String versionId, String reason) {
        if (documentRepository.markStatusIfParsing(documentId, "FAILED") == 0) {
            return false;
        }
        auditService.record("PARSE_FAILED", "DOCUMENT", documentId, documentId, "42201",
                Map.of("versionId", versionId, "reason", reason == null ? "" : trim(reason)));
        return true;
    }

    private static String trim(String value) {
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
