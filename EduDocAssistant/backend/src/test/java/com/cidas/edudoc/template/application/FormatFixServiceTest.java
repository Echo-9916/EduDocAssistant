package com.cidas.edudoc.template.application;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.template.application.FormatFixService.FixView;
import com.cidas.edudoc.template.application.impl.FormatFixServiceImpl;
import com.cidas.edudoc.template.infrastructure.DocxStyleEditor;
import com.cidas.edudoc.template.infrastructure.FormatCheck;
import com.cidas.edudoc.template.infrastructure.FormatCheckRepository;
import com.cidas.edudoc.template.infrastructure.FormatIssue;
import com.cidas.edudoc.template.infrastructure.FormatIssueRepository;
import com.cidas.edudoc.template.infrastructure.FormatRule;
import com.cidas.edudoc.template.infrastructure.FormatRuleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-05 一键修复：可修复筛选、版本前移、自动复审、并发/链分叉拒绝。 */
class FormatFixServiceTest {

    private static final String CHK = "A1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String DOC_ID = "B1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String VER_ID = "C1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String OBJ_ID = "D1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String TPL_VER = "E1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String I1 = "11B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String I2 = "12B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String I3 = "13B2C3D4E5F60708090A0B0C0D0E0F10";

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
    private final OssObjectRepository ossObjectRepository = mock(OssObjectRepository.class);
    private final FormatCheckRepository checkRepository = mock(FormatCheckRepository.class);
    private final FormatIssueRepository issueRepository = mock(FormatIssueRepository.class);
    private final FormatRuleRepository ruleRepository = mock(FormatRuleRepository.class);
    private final OssStorageAdapter ossStorageAdapter = mock(OssStorageAdapter.class);
    private final DocxStyleEditor styleEditor = mock(DocxStyleEditor.class);
    private final FormatFixTxWriter txWriter = mock(FormatFixTxWriter.class);

    private final FormatFixService service = new FormatFixServiceImpl(documentRepository, versionRepository,
            ossObjectRepository, checkRepository, issueRepository, ruleRepository, ossStorageAdapter,
            styleEditor, txWriter,
            new com.cidas.edudoc.infrastructure.oss.OssProperties("endpoint", "cn-beijing", "bucket",
                    "ak", "sk", "arn", "sts", "edudoc/uploads", 120, 900));

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private void owner() {
        AuthContext.set(new UserPrincipal("U1", "u", "U", Set.of("STUDENT"), Set.of(), Set.of()));
    }

    private void readyCheck(String status) {
        FormatCheck check = new FormatCheck();
        check.setId(CHK);
        check.setDocumentId(DOC_ID);
        check.setDocumentVersionId(VER_ID);
        check.setTemplateVersionId(TPL_VER);
        check.setStatus(status);
        when(checkRepository.findById(CHK)).thenReturn(Optional.of(check));
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, null, "U1", "报告", "REPORT", "READY", VER_ID)));
        when(versionRepository.findById(VER_ID)).thenReturn(Optional.of(
                new DocumentVersion(VER_ID, DOC_ID, 1, "UPLOAD", OBJ_ID, "U1", "hash", "A")));
        when(ossObjectRepository.findById(OBJ_ID)).thenReturn(Optional.of(new OssObject(
                OBJ_ID, "bucket", "cn-beijing", "edudoc/uploads/a.docx", null, "etag", "hash", 100,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "NONE",
                "ORIGINAL")));
    }

    private FormatIssue issue(String id, String ruleId, String fixable, String fixStatus, String locationRef) {
        return new FormatIssue(id, CHK, ruleId, "FONT", "PARAGRAPH", locationRef, "黑体", "宋体",
                "BLOCKER", fixable, fixStatus, "指引");
    }

    @Test
    void fixesOpenFixableIssuesAndSkipsRest() throws Exception {
        owner();
        readyCheck("FAILED");
        when(issueRepository.findByIds(anyList())).thenReturn(List.of(
                issue(I1, "R1", "Y", "OPEN", "{\"paragraphIndex\":1}"),
                issue(I2, "R1", "N", "OPEN", "{\"paragraphIndex\":2}"),
                issue(I3, "R1", "Y", "FIXED", "{\"paragraphIndex\":3}")));
        when(ruleRepository.findByIds(anyList())).thenReturn(List.of(
                new FormatRule("R1", TPL_VER, "FONT", "HEADING_1", "BLOCKER", "Y",
                        "{\"expect\":{\"family\":\"黑体\"},\"fix\":{\"family\":\"黑体\"}}", 10)));
        // downloadTo 为 void，mock 默认无动作；服务读空临时文件后由 editor/putBytes 桩接管
        when(styleEditor.apply(any(), anyList())).thenReturn("fixed".getBytes());
        when(ossStorageAdapter.putBytes(anyString(), any(), anyString()))
                .thenReturn(new OssStorageAdapter.ObjectInfo(5, "ETAG2", null, "SSE-OSS"));
        when(txWriter.apply(any())).thenReturn(new FormatFixTxWriter.FixOutcome("VER2", 2, "CHK2"));

        FixView view = service.fix(CHK, List.of(I1, I2, I3));

        assertEquals("VER2", view.newVersionId());
        assertEquals(List.of(I1), view.fixedIssueIds());
        assertEquals(List.of(I2, I3), view.skippedIssueIds());
        assertEquals("CHK2", view.recheckId());
        ArgumentCaptor<FormatFixTxWriter.FixRequest> captor =
                ArgumentCaptor.forClass(FormatFixTxWriter.FixRequest.class);
        verify(txWriter).apply(captor.capture());
        assertEquals(FormatFixService.DOCX_MIME, captor.getValue().object().getMimeType());
        verify(styleEditor).apply(any(), anyList());
    }

    @Test
    void rejectsWhenCheckStillRunning() {
        owner();
        readyCheck("QUEUED");
        var e = assertThrows(CidasBusinessException.class, () -> service.fix(CHK, List.of(I1)));
        assertEquals(40901, e.errorCode().code());
    }

    @Test
    void rejectsWhenCurrentVersionMoved() {
        owner();
        readyCheck("FAILED");
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, null, "U1", "报告", "REPORT", "READY", "F1B2C3D4E5F60708090A0B0C0D0E0F10")));
        var e = assertThrows(CidasBusinessException.class, () -> service.fix(CHK, List.of(I1)));
        assertEquals(40901, e.errorCode().code());
        verify(ossStorageAdapter, never()).putBytes(anyString(), any(), anyString());
    }

    @Test
    void rejectsNonDocxChainBranch() {
        owner();
        readyCheck("FAILED");
        when(ossObjectRepository.findById(OBJ_ID)).thenReturn(Optional.of(new OssObject(
                OBJ_ID, "bucket", "cn-beijing", "edudoc/uploads/a.txt", null, "etag", "hash", 100,
                "text/plain", "NONE", "PARSED")));
        var e = assertThrows(CidasBusinessException.class, () -> service.fix(CHK, List.of(I1)));
        assertEquals(40901, e.errorCode().code());
    }

    @Test
    void allSkippedProducesNoVersion() throws Exception {
        owner();
        readyCheck("FAILED");
        when(issueRepository.findByIds(anyList())).thenReturn(List.of(
                issue(I2, "R1", "N", "OPEN", "{\"paragraphIndex\":2}")));

        FixView view = service.fix(CHK, List.of(I2));

        assertEquals(List.of(), view.fixedIssueIds());
        assertEquals(List.of(I2), view.skippedIssueIds());
        verify(txWriter, never()).apply(any());
    }

    @Test
    void nonOwnerRejected() {
        AuthContext.set(new UserPrincipal("U2", "v", "V", Set.of("STUDENT"), Set.of(), Set.of()));
        readyCheck("FAILED");
        var e = assertThrows(CidasBusinessException.class, () -> service.fix(CHK, List.of(I1)));
        assertEquals(40401, e.errorCode().code());
    }
}
