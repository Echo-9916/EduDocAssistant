package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.ai.application.EmbeddingCleanupService;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.document.domain.Chunk;
import com.cidas.edudoc.document.infrastructure.DocumentChunkRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 1103 复审 N4：守卫跳过分支返回 false，且不动分块、不留审计。C-03：成功同事务补发向量化事件。 */
class DocumentParseTxServiceImplTest {

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final EmbeddingCleanupService embeddingCleanupService = mock(EmbeddingCleanupService.class);
    private final DocumentParseTxServiceImpl service = new DocumentParseTxServiceImpl(
            documentRepository, chunkRepository, auditService, outboxEventRepository, embeddingCleanupService);

    private static Chunk chunk() {
        return new Chunk(1, null, null, 0, 4, "text", 2, "hash");
    }

    @Test
    void writeSuccessReturnsFalseAndSkipsEverythingWhenNotParsing() {
        when(documentRepository.markStatusIfParsing("D1", "READY")).thenReturn(0);

        assertFalse(service.writeSuccess("D1", "V1", List.of(chunk())));
        verify(chunkRepository, never()).deleteByVersion(anyString());
        verify(chunkRepository, never()).insertAll(anyList());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
        verify(outboxEventRepository, never()).publish(any(), any(), any(), any());
    }

    @Test
    void writeSuccessWritesChunksPublishesEmbedEventAndReturnsTrue() {
        when(documentRepository.markStatusIfParsing("D1", "READY")).thenReturn(1);

        assertTrue(service.writeSuccess("D1", "V1", List.of(chunk())));
        // FK 无级联：先删向量行再删块（C-03 裁决）
        InOrder order = inOrder(embeddingCleanupService, chunkRepository);
        order.verify(embeddingCleanupService).deleteByVersion("V1");
        order.verify(chunkRepository).deleteByVersion("V1");
        verify(chunkRepository).insertAll(anyList());
        verify(outboxEventRepository).publish(eq("DOCUMENT_VERSION"), eq("V1"),
                eq("DOCUMENT_EMBED_REQUESTED"), argThatPayload());
        verify(auditService).record(eq("PARSE_SUCCEEDED"), eq("DOCUMENT"), eq("D1"), eq("D1"),
                eq("200"), any(Map.class));
    }

    private static Map<String, Object> argThatPayload() {
        return org.mockito.ArgumentMatchers.argThat(payload ->
                payload != null && "D1".equals(payload.get("documentId")) && "V1".equals(payload.get("versionId")));
    }

    @Test
    void writeFailureSkipsAuditWhenNotParsing() {
        when(documentRepository.markStatusIfParsing("D1", "FAILED")).thenReturn(0);

        assertFalse(service.writeFailure("D1", "V1", "boom"));
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
        verify(outboxEventRepository, never()).publish(any(), any(), any(), any());
    }
}
