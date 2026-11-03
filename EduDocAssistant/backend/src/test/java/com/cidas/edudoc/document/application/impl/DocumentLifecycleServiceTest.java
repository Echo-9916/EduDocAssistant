package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentChunkRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B-08：软删/恢复/重解析的状态与并发分支。 */
class DocumentLifecycleServiceTest {

    private static final String DOC_ID = "D1EAD1EAD1EAD1EAD1EAD1EAD1EAD1EA";

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
    private final DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
    private final OssObjectRepository ossObjectRepository = mock(OssObjectRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final DocumentLifecycleServiceImpl service = new DocumentLifecycleServiceImpl(
            documentRepository, chunkRepository, versionRepository, ossObjectRepository,
            outboxEventRepository, auditService);

    @BeforeEach
    void setUp() {
        AuthContext.set(new UserPrincipal("U1", "teacher", null, Set.of("TEACHER"), Set.of(), Set.of()));
    }

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    private static Document doc(String status) {
        return new Document(DOC_ID, null, "U1", "t", "COURSEWARE", status, "V1");
    }

    @Test
    void deleteSoftDeletesAndAuditsPrevStatus() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc("READY")));
        when(documentRepository.softDelete(DOC_ID)).thenReturn(1);

        service.delete(DOC_ID);

        verify(documentRepository).softDelete(DOC_ID);
        verify(auditService).record(eq("DOCUMENT_DELETED"), eq("DOCUMENT"), eq(DOC_ID), eq(DOC_ID),
                eq("200"), eq(Map.of("prevStatus", "READY")));
    }

    @Test
    void deleteAlreadyTrashedConflicts() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc("DELETED")));

        assertCode(ErrorCode.CONFLICT, () -> service.delete(DOC_ID));
        verify(documentRepository, never()).softDelete(anyString());
    }

    @Test
    void malformedIdIsBadRequestNotDatabaseError() {
        assertCode(ErrorCode.BAD_REQUEST, () -> service.delete("x"));
        assertCode(ErrorCode.BAD_REQUEST, () -> service.restore("not-hex"));
        assertCode(ErrorCode.BAD_REQUEST, () -> service.reparse("zz", null));
    }

    @Test
    void deleteOtherUsersDocumentIsNotFound() {
        Document other = doc("READY");
        other.setOwnerId("OTHER");
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(other));

        assertCode(ErrorCode.NOT_FOUND, () -> service.delete(DOC_ID));
    }

    @Test
    void restoreWithChunksBecomesReady() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc("DELETED")));
        when(chunkRepository.countByVersion("V1")).thenReturn(4);
        when(documentRepository.restore(DOC_ID, "READY")).thenReturn(1);

        service.restore(DOC_ID);

        verify(auditService).record(eq("DOCUMENT_RESTORED"), eq("DOCUMENT"), eq(DOC_ID), eq(DOC_ID),
                eq("200"), eq(Map.of("restoredStatus", "READY")));
    }

    @Test
    void restoreWithoutChunksBecomesFailed() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc("DELETED")));
        when(chunkRepository.countByVersion("V1")).thenReturn(0);
        when(documentRepository.restore(DOC_ID, "FAILED")).thenReturn(1);

        service.restore(DOC_ID);

        verify(documentRepository).restore(DOC_ID, "FAILED");
    }

    @Test
    void restoreNotInTrashConflicts() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc("READY")));

        assertCode(ErrorCode.CONFLICT, () -> service.restore(DOC_ID));
    }

    @Test
    void restoreWithSameNameActiveDocumentConflicts() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc("DELETED")));
        when(chunkRepository.countByVersion("V1")).thenReturn(1);
        when(documentRepository.restore(DOC_ID, "READY")).thenThrow(new DuplicateKeyException("uk"));

        CidasBusinessException e = assertThrows(CidasBusinessException.class, () -> service.restore(DOC_ID));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
        assertEquals("已存在同名活跃文档，无法恢复", e.getMessage());
    }

    @Test
    void reparsePublishesEventAndReturnsTaskId() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc("FAILED")));
        when(versionRepository.findById("V1")).thenReturn(Optional.of(
                new DocumentVersion("V1", DOC_ID, 1, "UPLOAD", "O1", "U1", "h", "A")));
        when(ossObjectRepository.findById("O1")).thenReturn(Optional.of(
                new OssObject("O1", "cidas-oss-bucket", "cn-beijing", "k/b.pdf", null,
                        "E", "h", 10, "application/pdf", "NONE", "ORIGINAL")));
        when(outboxEventRepository.publish(eq("DOCUMENT_VERSION"), eq("V1"),
                eq("DOCUMENT_PARSE_REQUESTED"), any(Map.class))).thenReturn("T1");

        String taskId = service.reparse(DOC_ID, null);

        assertEquals("T1", taskId);
        verify(documentRepository).updateStatus(DOC_ID, "PARSING");
        verify(auditService).record(eq("PARSE_REQUESTED"), eq("DOCUMENT"), eq(DOC_ID), eq(DOC_ID),
                eq("200"), eq(Map.of("versionId", "V1", "taskId", "T1")));
    }

    @Test
    void reparseWhileParsingConflicts() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc("PARSING")));

        assertCode(ErrorCode.CONFLICT, () -> service.reparse(DOC_ID, null));
    }

    @Test
    void reparseNonCurrentVersionConflicts() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc("READY")));

        assertCode(ErrorCode.CONFLICT, () -> service.reparse(DOC_ID, "V9"));
    }

    @Test
    void reparseWithMissingSourceObjectConflicts() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc("READY")));
        when(versionRepository.findById("V1")).thenReturn(Optional.empty());

        assertCode(ErrorCode.CONFLICT, () -> service.reparse(DOC_ID, null));
        verify(outboxEventRepository, never()).publish(anyString(), anyString(), anyString(), any());
    }

    private static void assertCode(ErrorCode expected, Runnable action) {
        CidasBusinessException e = assertThrows(CidasBusinessException.class, action::run);
        assertEquals(expected, e.errorCode());
    }
}
