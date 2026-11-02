package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.infrastructure.CourseRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.application.DocumentProvisioningService;
import com.cidas.edudoc.document.application.DocumentProvisioningService.CreatedDocument;
import com.cidas.edudoc.document.application.UploadSessionService.CompleteCommand;
import com.cidas.edudoc.document.application.UploadSessionService.CompletionResult;
import com.cidas.edudoc.document.domain.DocumentType;
import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.document.infrastructure.UploadSessionRepository;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter.ObjectInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** B-04 complete 回调：会话锁、大小/摘要校验、恢复路径与重放（对应详细设计 UT-01/UT-02）。 */
class CompleteUploadTest {

    private static final String SHA = "a".repeat(64);
    private static final String SHA_UPPER = SHA.toUpperCase();

    private final UploadSessionRepository sessionRepository = mock(UploadSessionRepository.class);
    private final OssStorageAdapter ossStorageAdapter = mock(OssStorageAdapter.class);
    private final DocumentProvisioningService provisioningService = mock(DocumentProvisioningService.class);
    private final UploadSessionServiceImpl service = new UploadSessionServiceImpl(sessionRepository,
            ossStorageAdapter, mock(CourseRepository.class),
            mock(OssProperties.class), provisioningService);

    private static UploadSession session(UploadSession.Status status) {
        return new UploadSession("S1", "U1", "C1", "标题", DocumentType.COURSEWARE, "a.pdf", 1024,
                "application/pdf", "edudoc/uploads/202611/U1/S1-a.pdf", status, null, null, Instant.now());
    }

    @BeforeEach
    void setUp() {
        AuthContext.set(new UserPrincipal("U1", "teacher", null, Set.of("TEACHER"), Set.of(), Set.of("C1")));
        when(sessionRepository.compareAndSet(any(), any())).thenReturn(true);
    }

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    @Test
    void happyPathLocksVerifiesProvisionsAndMarksCompleted() {
        when(sessionRepository.find("S1")).thenReturn(Optional.of(session(UploadSession.Status.OPEN)));
        when(ossStorageAdapter.headObject(anyString()))
                .thenReturn(Optional.of(new ObjectInfo(1024, "ETAG", null, "AES256")));
        when(ossStorageAdapter.sha256Of(anyString())).thenReturn(SHA);
        when(provisioningService.provision(any(), any(), anyString())).thenReturn(new CreatedDocument("D1", "V1"));

        CompletionResult result = service.completeUpload("S1", new CompleteCommand("ETAG", SHA_UPPER));

        assertEquals("D1", result.documentId());
        assertEquals("V1", result.versionId());
        // 服务端实算摘要为准：provision 收到的是实算值而非浏览器上报值
        verify(provisioningService).provision(any(), any(), org.mockito.ArgumentMatchers.eq(SHA));
    }

    @Test
    void writesBackCompletedWithIdsAfterProvision() {
        when(sessionRepository.find("S1")).thenReturn(Optional.of(session(UploadSession.Status.OPEN)));
        when(ossStorageAdapter.headObject(anyString()))
                .thenReturn(Optional.of(new ObjectInfo(1024, "ETAG", null, null)));
        when(ossStorageAdapter.sha256Of(anyString())).thenReturn(SHA);
        when(provisioningService.provision(any(), any(), anyString())).thenReturn(new CreatedDocument("D1", "V1"));

        service.completeUpload("S1", new CompleteCommand("ETAG", SHA));

        ArgumentCaptor<UploadSession> next = ArgumentCaptor.forClass(UploadSession.class);
        verify(sessionRepository, org.mockito.Mockito.atLeast(2))
                .compareAndSet(any(UploadSession.class), next.capture());
        UploadSession finalState = next.getAllValues().get(next.getAllValues().size() - 1);
        assertEquals(UploadSession.Status.COMPLETED, finalState.status());
        assertEquals("D1", finalState.completedDocumentId());
        assertEquals("V1", finalState.completedVersionId());
    }

    @Test
    void replayReturnsStoredIdsWithoutTouchingOss() {
        UploadSession completed = session(UploadSession.Status.OPEN)
                .completed("D9", "V9").withStatus(UploadSession.Status.COMPLETED);
        when(sessionRepository.find("S1")).thenReturn(Optional.of(completed));

        CompletionResult result = service.completeUpload("S1", new CompleteCommand(null, SHA));

        assertEquals("D9", result.documentId());
        verifyNoInteractions(ossStorageAdapter);
    }

    @Test
    void rejectsInvalidContentHashFormat() {
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.completeUpload("S1", new CompleteCommand(null, "not-a-hash")));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
        verify(sessionRepository, org.mockito.Mockito.never()).find(anyString());
    }

    @Test
    void missingSessionIsNotFound() {
        when(sessionRepository.find("S1")).thenReturn(Optional.empty());

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.completeUpload("S1", new CompleteCommand(null, SHA)));
        assertEquals(ErrorCode.NOT_FOUND, e.errorCode());
    }

    @Test
    void otherUsersSessionIsForbidden() {
        when(sessionRepository.find("S1")).thenReturn(Optional.of(
                new UploadSession("S1", "OTHER", "C1", "标题", DocumentType.COURSEWARE, "a.pdf", 1024,
                        "application/pdf", "k", UploadSession.Status.OPEN, null, null, Instant.now())));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.completeUpload("S1", new CompleteCommand(null, SHA)));
        assertEquals(ErrorCode.FORBIDDEN, e.errorCode());
    }

    @Test
    void sizeMismatchReopensSessionAndRejects() {
        when(sessionRepository.find("S1")).thenReturn(Optional.of(session(UploadSession.Status.OPEN)));
        when(ossStorageAdapter.headObject(anyString()))
                .thenReturn(Optional.of(new ObjectInfo(2048, "ETAG", null, null)));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.completeUpload("S1", new CompleteCommand(null, SHA)));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
        assertReopenedToOpen();
    }

    @Test
    void hashMismatchReopensSessionAndRejects() {
        when(sessionRepository.find("S1")).thenReturn(Optional.of(session(UploadSession.Status.OPEN)));
        when(ossStorageAdapter.headObject(anyString()))
                .thenReturn(Optional.of(new ObjectInfo(1024, "ETAG", null, null)));
        when(ossStorageAdapter.sha256Of(anyString())).thenReturn("b".repeat(64));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.completeUpload("S1", new CompleteCommand(null, SHA)));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
        assertReopenedToOpen();
    }

    @Test
    void ossReadFailureReopensAndKeepsUnavailable() {
        when(sessionRepository.find("S1")).thenReturn(Optional.of(session(UploadSession.Status.OPEN)));
        when(ossStorageAdapter.headObject(anyString()))
                .thenReturn(Optional.of(new ObjectInfo(1024, "ETAG", null, null)));
        when(ossStorageAdapter.sha256Of(anyString()))
                .thenThrow(new CidasBusinessException(ErrorCode.SERVICE_UNAVAILABLE, "OSS 对象读取失败"));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.completeUpload("S1", new CompleteCommand(null, SHA)));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, e.errorCode());
        assertReopenedToOpen();
    }

    @Test
    void duplicateTitleMapsToConflictWithTrashHint() {
        when(sessionRepository.find("S1")).thenReturn(Optional.of(session(UploadSession.Status.OPEN)));
        when(ossStorageAdapter.headObject(anyString()))
                .thenReturn(Optional.of(new ObjectInfo(1024, "ETAG", null, null)));
        when(ossStorageAdapter.sha256Of(anyString())).thenReturn(SHA);
        when(provisioningService.provision(any(), any(), anyString()))
                .thenThrow(new DuplicateKeyException("uk_document_1"));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.completeUpload("S1", new CompleteCommand(null, SHA)));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
        assertTrue(e.getMessage().contains("回收站"));
        assertReopenedToOpen();
    }

    @Test
    void stuckCompletingWithCommittedObjectRecoversByReplay() {
        when(sessionRepository.find("S1")).thenReturn(Optional.of(session(UploadSession.Status.COMPLETING)));
        when(provisioningService.findByObjectKey("edudoc/uploads/202611/U1/S1-a.pdf"))
                .thenReturn(Optional.of(new CreatedDocument("D7", "V7")));

        CompletionResult result = service.completeUpload("S1", new CompleteCommand(null, SHA));

        assertEquals("D7", result.documentId());
        verifyNoInteractions(ossStorageAdapter);
    }

    @Test
    void stuckCompletingWithoutCommittedObjectTellsRetry() {
        when(sessionRepository.find("S1")).thenReturn(Optional.of(session(UploadSession.Status.COMPLETING)));
        when(provisioningService.findByObjectKey(anyString())).thenReturn(Optional.empty());

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.completeUpload("S1", new CompleteCommand(null, SHA)));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
    }

    private void assertReopenedToOpen() {
        ArgumentCaptor<UploadSession> next = ArgumentCaptor.forClass(UploadSession.class);
        verify(sessionRepository, org.mockito.Mockito.atLeast(2))
                .compareAndSet(any(UploadSession.class), next.capture());
        UploadSession last = next.getAllValues().get(next.getAllValues().size() - 1);
        assertEquals(UploadSession.Status.OPEN, last.status());
    }
}
