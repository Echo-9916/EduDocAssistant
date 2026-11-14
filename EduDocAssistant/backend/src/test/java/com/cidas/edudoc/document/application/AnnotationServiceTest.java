package com.cidas.edudoc.document.application;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.application.impl.AnnotationServiceImpl;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentAnnotationRepository;
import com.cidas.edudoc.document.infrastructure.DocumentAnnotationRow;
import com.cidas.edudoc.document.infrastructure.DocumentChunk;
import com.cidas.edudoc.document.infrastructure.DocumentChunkRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 批注：owner/协作者入口、chunk 区间校验、作者/owner 删除口径。 */
class AnnotationServiceTest {

    private static final String DOC = "AA0000000000000000000000000000A1";
    private static final String VER = "BB0000000000000000000000000000B1";
    private static final String AN = "CC0000000000000000000000000000C1";

    private final CollaborationService collaborationService = mock(CollaborationService.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
    private final DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
    private final DocumentAnnotationRepository annotationRepository = mock(DocumentAnnotationRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AnnotationService service = new AnnotationServiceImpl(collaborationService,
            documentRepository, versionRepository, chunkRepository, annotationRepository, auditService);

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    private static void login(String userId) {
        AuthContext.set(new UserPrincipal(userId, "u", "用户", Set.of("STUDENT"),
                Set.of("DOCUMENT_READ"), Set.of()));
    }

    private static Document doc(String owner) {
        return new Document(DOC, null, owner, "报告", "REPORT", "READY", VER);
    }

    private static DocumentAnnotationRow row(String status, String author) {
        DocumentAnnotationRow row = new DocumentAnnotationRow();
        row.setId(AN);
        row.setDocumentId(DOC);
        row.setDocumentVersionId(VER);
        row.setAuthorId(author);
        row.setAuthorName("作者");
        row.setCharStart(0);
        row.setCharEnd(5);
        row.setContent("建议补充");
        row.setStatus(status);
        return row;
    }

    @Test
    void ownerCreatesAnnotationOnChunkRange() {
        login("U1");
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(doc("U1")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(
                new DocumentVersion(VER, DOC, 1, "UPLOAD", "O1", "U1", "h", "A")));
        when(chunkRepository.selectAllByVersion(VER)).thenReturn(List.of(
                new DocumentChunk("C1", VER, 1, null, 1, 0, 10, "hello", 2, "h", "READY")));
        when(annotationRepository.create(eq(DOC), eq(VER), eq("U1"), eq(2), eq(4), eq("建议补充")))
                .thenReturn(AN);
        when(annotationRepository.findById(AN)).thenReturn(row("O", "U1"));

        AnnotationService.AnnotationView view = service.create(DOC,
                new AnnotationService.CreateCommand(null, 2, 4, " 建议补充 "));

        assertEquals(AN, view.id());
        assertEquals("O", view.status());
        verify(auditService).record(eq("ANNOTATION_CREATED"), eq("DOCUMENT"), eq(DOC), eq(DOC),
                eq("200"), any());
    }

    @Test
    void rangeOutsideChunksIsBadRequest() {
        login("U1");
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(doc("U1")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(
                new DocumentVersion(VER, DOC, 1, "UPLOAD", "O1", "U1", "h", "A")));
        when(chunkRepository.selectAllByVersion(VER)).thenReturn(List.of(
                new DocumentChunk("C1", VER, 1, null, 1, 0, 10, "hello", 2, "h", "READY")));

        assertCode(ErrorCode.BAD_REQUEST, () -> service.create(DOC,
                new AnnotationService.CreateCommand(null, 20, 30, "越界")));
        verify(annotationRepository, never()).create(anyString(), anyString(), anyString(),
                anyInt(), anyInt(), anyString());
    }

    @Test
    void outsiderHasNoReadAccess() {
        login("U2");
        doThrow(new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"))
                .when(collaborationService).requireReadable(DOC);

        assertCode(ErrorCode.NOT_FOUND, () -> service.list(DOC, null));
    }

    @Test
    void collaboratorCannotDeleteOthersAnnotation() {
        login("U2");
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(doc("U1")));
        when(annotationRepository.findById(AN)).thenReturn(row("O", "U1"));

        assertCode(ErrorCode.FORBIDDEN, () -> service.delete(AN));
        verify(annotationRepository, never()).delete(AN);
    }

    @Test
    void ownerCannotDeleteOthersAnnotation() {
        // 删除权只归作者本人，owner 也不例外
        login("U1");
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(doc("U1")));
        when(annotationRepository.findById(AN)).thenReturn(row("O", "U3"));

        assertCode(ErrorCode.FORBIDDEN, () -> service.delete(AN));
        verify(annotationRepository, never()).delete(AN);
    }

    @Test
    void authorDeletesOwnAnnotation() {
        login("U1");
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(doc("U1")));
        when(annotationRepository.findById(AN)).thenReturn(row("O", "U1"));

        service.delete(AN);

        verify(annotationRepository).delete(AN);
    }

    @Test
    void collaboratorCannotResolveOthersAnnotation() {
        // 解决/重开仅 owner 或作者；普通协作者不行
        login("U2");
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(doc("U1")));
        when(annotationRepository.findById(AN)).thenReturn(row("O", "U3"));

        assertCode(ErrorCode.FORBIDDEN, () -> service.resolve(AN, true));
        verify(annotationRepository, never()).resolve(AN, "U2");
    }

    @Test
    void ownerCanResolveOthersAnnotation() {
        login("U1");
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(doc("U1")));
        when(annotationRepository.findById(AN)).thenReturn(row("O", "U3"), row("R", "U3"));

        AnnotationService.AnnotationView view = service.resolve(AN, true);

        assertEquals("R", view.status());
        verify(annotationRepository).resolve(AN, "U1");
    }

    @Test
    void resolveWritesResolvedState() {
        login("U1");
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(doc("U1")));
        when(annotationRepository.findById(AN)).thenReturn(row("O", "U1"), row("R", "U1"));

        AnnotationService.AnnotationView view = service.resolve(AN, true);

        assertEquals("R", view.status());
        verify(annotationRepository).resolve(AN, "U1");
    }

    private static void assertCode(ErrorCode expected, Runnable action) {
        CidasBusinessException e = assertThrows(CidasBusinessException.class, action::run);
        assertEquals(expected, e.errorCode());
    }
}