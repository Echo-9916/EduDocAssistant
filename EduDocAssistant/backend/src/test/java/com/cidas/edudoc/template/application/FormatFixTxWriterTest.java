package com.cidas.edudoc.template.application;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.template.application.FormatFixTxWriter.FixOutcome;
import com.cidas.edudoc.template.application.FormatFixTxWriter.FixRequest;
import com.cidas.edudoc.template.infrastructure.FormatCheck;
import com.cidas.edudoc.template.infrastructure.FormatCheckRepository;
import com.cidas.edudoc.template.infrastructure.FormatIssueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 终审 I1/Minor(FormatFixTxWriter 零单测) 整改：修复成版五写 + 复审事件 + 审计单事务；
 * current 前移必须 CAS（expected=被校验版本），并发推进 → 40901 回滚，杜绝同基版双 EDIT。
 */
class FormatFixTxWriterTest {

    private static final String DOC_ID = "A1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String OLD_VER = "B1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String OBJ_ID = "C1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String TPL_VER = "D1B2C3D4E5F60708090A0B0C0D0E0F10";

    private final OssObjectRepository ossObjectRepository = mock(OssObjectRepository.class);
    private final DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final FormatCheckRepository checkRepository = mock(FormatCheckRepository.class);
    private final FormatIssueRepository issueRepository = mock(FormatIssueRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final AuditService auditService = mock(AuditService.class);

    private final FormatFixTxWriter writer = new FormatFixTxWriter(ossObjectRepository, versionRepository,
            documentRepository, checkRepository, issueRepository, outboxEventRepository, auditService);

    private FixRequest request() {
        OssObject object = new OssObject(OBJ_ID, "bucket", "cn-beijing", "edudoc/documents/fix/a.docx",
                null, "etag", "hash", 10, FormatFixService.DOCX_MIME, "NONE", "ORIGINAL");
        DocumentVersion version = new DocumentVersion("VER_NEW", DOC_ID, 2, "EDIT", OBJ_ID, "U1", "hash", "A");
        FormatCheck recheck = new FormatCheck();
        recheck.setDocumentId(DOC_ID);
        recheck.setTemplateVersionId(TPL_VER);
        recheck.setRequestedBy("U1");
        recheck.setStatus("QUEUED");
        return new FixRequest(object, version, List.of("I1"), recheck, "CHK1", OLD_VER);
    }

    @Test
    void appliesFiveWritesRecheckEventAndAudit() {
        when(documentRepository.updateCurrentVersionCas(eq(DOC_ID), eq(OLD_VER), eq("VER_NEW"))).thenReturn(1);
        when(checkRepository.insert(any())).thenReturn("CHK2");

        FixOutcome outcome = writer.apply(request());

        assertEquals("VER_NEW", outcome.versionId());
        assertEquals("CHK2", outcome.recheckId());
        assertEquals(2, outcome.versionNo());
        verify(ossObjectRepository).insert(any(OssObject.class));
        verify(versionRepository).insert(any(DocumentVersion.class));
        verify(documentRepository).updateStatus(DOC_ID, "PARSING");
        verify(issueRepository).markFixed(List.of("I1"));
        verify(outboxEventRepository).publish(eq("DOCUMENT_VERSION"), eq("VER_NEW"),
                eq("DOCUMENT_PARSE_REQUESTED"), any(Map.class));
        verify(outboxEventRepository).publish(eq("FORMAT_CHECK"), eq("CHK2"),
                eq("FORMAT_CHECK_REQUESTED"), any(Map.class));
        verify(auditService).record(eq("FORMAT_FIX_APPLIED"), eq("DOCUMENT"), eq(DOC_ID), eq(DOC_ID),
                eq("200"), any(Map.class));
    }

    @Test
    void concurrentAdvanceRollsBackWithConflict() {
        when(documentRepository.updateCurrentVersionCas(anyString(), anyString(), anyString())).thenReturn(0);

        var e = assertThrows(CidasBusinessException.class, () -> writer.apply(request()));

        assertEquals(40901, e.errorCode().code());
        verify(checkRepository, never()).insert(any());
        verify(outboxEventRepository, never()).publish(any(), any(), any(), any());
    }

    @Test
    void duplicateVersionNumberMapsToConflict() {
        doThrow(new DuplicateKeyException("uk_docver")).when(versionRepository).insert(any());

        var e = assertThrows(CidasBusinessException.class, () -> writer.apply(request()));

        assertEquals(40901, e.errorCode().code());
    }
}
