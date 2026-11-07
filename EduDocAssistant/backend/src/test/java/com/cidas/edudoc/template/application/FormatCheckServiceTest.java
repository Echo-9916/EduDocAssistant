package com.cidas.edudoc.template.application;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.template.application.FormatCheckService.CheckView;
import com.cidas.edudoc.template.application.impl.FormatCheckServiceImpl;
import com.cidas.edudoc.template.infrastructure.FormatCheck;
import com.cidas.edudoc.template.infrastructure.FormatCheckRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-04 校验任务创建：模板选择口径、非 docx 落 ERROR（J1）、活跃去重、READY/可见性门。 */
class FormatCheckServiceTest {

    private static final String DOC_ID = "A1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String VER_ID = "B1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String OBJ_ID = "C1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String COURSE = "D1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String TPL_VER = "E1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String CHECK_HEX = "11B2C3D4E5F60708090A0B0C0D0E0F10";

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
    private final OssObjectRepository ossObjectRepository = mock(OssObjectRepository.class);
    private final FormatCheckRepository checkRepository = mock(FormatCheckRepository.class);
    private final com.cidas.edudoc.template.infrastructure.FormatIssueRepository issueRepository =
            mock(com.cidas.edudoc.template.infrastructure.FormatIssueRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final FormatCheckService service = new FormatCheckServiceImpl(
            documentRepository, versionRepository, ossObjectRepository, checkRepository,
            issueRepository, outboxEventRepository);

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private void owner(String userId) {
        AuthContext.set(new UserPrincipal(userId, "u", "U", Set.of("STUDENT"), Set.of(), Set.of()));
    }

    private void docx(String status) {
        when(checkRepository.selectCoursePublishedTemplate(COURSE)).thenReturn(TPL_VER);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, COURSE, "U1", "报告", "REPORT", status, VER_ID)));
        when(versionRepository.findById(VER_ID)).thenReturn(Optional.of(
                new DocumentVersion(VER_ID, DOC_ID, 1, "UPLOAD", OBJ_ID, "U1", "hash", "A")));
        when(ossObjectRepository.findById(OBJ_ID)).thenReturn(Optional.of(new OssObject(
                OBJ_ID, "bucket", "cn-beijing", "edudoc/uploads/a.docx", null, "etag", "hash",
                100, "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "NONE", "ORIGINAL")));
    }

    @Test
    void createsQueuedCheckAndPublishesEvent() {
        owner("U1");
        docx("READY");
        when(checkRepository.findActive(VER_ID, TPL_VER, "U1")).thenReturn(Optional.empty());
        when(checkRepository.insert(any())).thenReturn("CH1");

        CheckView view = service.create(DOC_ID);

        assertEquals("QUEUED", view.status());
        ArgumentCaptor<FormatCheck> captor = ArgumentCaptor.forClass(FormatCheck.class);
        verify(checkRepository).insert(captor.capture());
        assertEquals(TPL_VER, captor.getValue().getTemplateVersionId());
        verify(outboxEventRepository).publish(eq("FORMAT_CHECK"), eq("CH1"), eq("FORMAT_CHECK_REQUESTED"),
                any(Map.class));
        // 1106 审查 P3-1：去重前必须锁文档行
        verify(documentRepository).lockForUpdate(DOC_ID);
    }

    /** 概要 §4.3 P4：文档已绑定模板版本时优先用绑定版本，新发布不影响历史绑定。 */
    @Test
    void boundTemplateVersionWinsOverCourseCurrent() {
        owner("U1");
        docx("READY");
        String boundVer = "F1B2C3D4E5F60708090A0B0C0D0E0F10";
        when(checkRepository.selectDocumentTemplateBinding(DOC_ID)).thenReturn(boundVer);
        when(checkRepository.findActive(VER_ID, boundVer, "U1")).thenReturn(Optional.empty());
        when(checkRepository.insert(any())).thenReturn("CH1");

        service.create(DOC_ID);

        ArgumentCaptor<FormatCheck> captor = ArgumentCaptor.forClass(FormatCheck.class);
        verify(checkRepository).insert(captor.capture());
        assertEquals(boundVer, captor.getValue().getTemplateVersionId());
    }

    /** 终审 Minor（UT-13 前半显式化）：模板停用/无当前发布版本时，历史绑定版本仍用于校验。 */
    @Test
    void boundTemplateStillUsedWhenCourseHasNoPublishedVersion() {
        owner("U1");
        docx("READY");
        String boundVer = "F1B2C3D4E5F60708090A0B0C0D0E0F10";
        when(checkRepository.selectDocumentTemplateBinding(DOC_ID)).thenReturn(boundVer);
        when(checkRepository.selectCoursePublishedTemplate(COURSE)).thenReturn(null);
        when(checkRepository.findActive(VER_ID, boundVer, "U1")).thenReturn(Optional.empty());
        when(checkRepository.insert(any())).thenReturn("CH3");

        service.create(DOC_ID);

        ArgumentCaptor<FormatCheck> captor = ArgumentCaptor.forClass(FormatCheck.class);
        verify(checkRepository).insert(captor.capture());
        assertEquals(boundVer, captor.getValue().getTemplateVersionId());
    }

    /** 终审 Minor：非 docx 且无模板时提示两个事实（不再只报模板误导 J1 语义）。 */
    @Test
    void nonDocxWithoutTemplateMentionsBothFacts() {
        owner("U1");
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, COURSE, "U1", "课件", "COURSEWARE", "READY", VER_ID)));
        when(versionRepository.findById(VER_ID)).thenReturn(Optional.of(
                new DocumentVersion(VER_ID, DOC_ID, 1, "UPLOAD", OBJ_ID, "U1", "hash", "A")));
        when(ossObjectRepository.findById(OBJ_ID)).thenReturn(Optional.of(new OssObject(
                OBJ_ID, "bucket", "cn-beijing", "edudoc/uploads/a.pdf", null, "etag", "hash", 100,
                "application/pdf", "NONE", "ORIGINAL")));
        when(checkRepository.selectCoursePublishedTemplate(COURSE)).thenReturn(null);

        var e = assertThrows(CidasBusinessException.class, () -> service.create(DOC_ID));
        assertEquals(40901, e.errorCode().code());
        assertTrue(e.getMessage().contains("Word"));
    }

    @Test
    void noTemplateAvailableRejectsWithoutInsert() {
        owner("U1");
        docx("READY");
        when(checkRepository.selectDocumentTemplateBinding(DOC_ID)).thenReturn(null);
        when(checkRepository.selectCoursePublishedTemplate(COURSE)).thenReturn(null);

        var e = assertThrows(CidasBusinessException.class, () -> service.create(DOC_ID));
        assertEquals(40901, e.errorCode().code());
        verify(checkRepository, never()).insert(any());
    }

    @Test
    void nonDocxPersistsErrorWithoutEvent() {
        owner("U1");
        when(checkRepository.selectCoursePublishedTemplate(COURSE)).thenReturn(TPL_VER);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, COURSE, "U1", "课件", "COURSEWARE", "READY", VER_ID)));
        when(versionRepository.findById(VER_ID)).thenReturn(Optional.of(
                new DocumentVersion(VER_ID, DOC_ID, 1, "UPLOAD", OBJ_ID, "U1", "hash", "A")));
        when(ossObjectRepository.findById(OBJ_ID)).thenReturn(Optional.of(new OssObject(
                OBJ_ID, "bucket", "cn-beijing", "edudoc/uploads/a.pdf", null, "etag", "hash",
                100, "application/pdf", "NONE", "ORIGINAL")));
        when(checkRepository.insertError(any())).thenReturn("CH2");

        CheckView view = service.create(DOC_ID);

        assertEquals("ERROR", view.status());
        verify(outboxEventRepository, never()).publish(any(), any(), any(), any());
    }

    @Test
    void activeCheckReusedNotDuplicated() {
        owner("U1");
        docx("READY");
        FormatCheck active = new FormatCheck();
        active.setId("CH9");
        active.setStatus("RUNNING");
        when(checkRepository.findActive(VER_ID, TPL_VER, "U1")).thenReturn(Optional.of(active));

        CheckView view = service.create(DOC_ID);

        assertEquals("CH9", view.id());
        assertEquals("RUNNING", view.status());
        verify(checkRepository, never()).insert(any());
    }

    @Test
    void nonOwnerRejectedInvisible() {
        owner("U2");
        docx("READY");
        var e = assertThrows(CidasBusinessException.class, () -> service.create(DOC_ID));
        assertEquals(40401, e.errorCode().code());
    }

    @Test
    void notReadyDocumentRejected() {
        owner("U1");
        docx("PARSING");
        var e = assertThrows(CidasBusinessException.class, () -> service.create(DOC_ID));
        assertEquals(40901, e.errorCode().code());
    }

    @Test
    void getCheckEnforcesVisibility() {
        owner("U2");
        FormatCheck check = new FormatCheck();
        check.setId(CHECK_HEX);
        check.setDocumentId(DOC_ID);
        check.setStatus("PASSED");
        when(checkRepository.findById(CHECK_HEX)).thenReturn(Optional.of(check));
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, COURSE, "U1", "报告", "REPORT", "READY", VER_ID)));
        var e = assertThrows(CidasBusinessException.class, () -> service.get(CHECK_HEX));
        assertEquals(40401, e.errorCode().code());
    }

    @Test
    void invalidCheckIdRejectedAsParamError() {
        owner("U1");
        var e = assertThrows(CidasBusinessException.class, () -> service.get("bad-id"));
        assertEquals(40001, e.errorCode().code());
    }
}
