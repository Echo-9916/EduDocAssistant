package com.cidas.edudoc.template.application;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.application.DocumentProvisioningService.CreatedDocument;
import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.template.application.TemplateProvisionService.ProvisionView;
import com.cidas.edudoc.template.application.impl.TemplateProvisionServiceImpl;
import com.cidas.edudoc.template.application.impl.TemplateProvisionTxWriter;
import com.cidas.edudoc.template.infrastructure.Template;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-07 按模板新建：J4 生成建档链路、课程/状态门、绑定模板版本、同名 40901。 */
class TemplateProvisionTest {

    private static final String TPL = "A1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String COURSE = "B1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String TPL_VER = "C1B2C3D4E5F60708090A0B0C0D0E0F10";

    private final com.cidas.edudoc.template.infrastructure.TemplateRepository templateRepository =
            mock(com.cidas.edudoc.template.infrastructure.TemplateRepository.class);
    private final com.cidas.edudoc.template.infrastructure.FormatRuleRepository ruleRepository =
            mock(com.cidas.edudoc.template.infrastructure.FormatRuleRepository.class);
    private final OssStorageAdapter ossStorageAdapter = mock(OssStorageAdapter.class);
    private final com.cidas.edudoc.template.infrastructure.TemplateOutlineGenerator generator =
            mock(com.cidas.edudoc.template.infrastructure.TemplateOutlineGenerator.class);
    private final TemplateProvisionTxWriter provisionTxWriter = mock(TemplateProvisionTxWriter.class);

    private final TemplateProvisionService service = new TemplateProvisionServiceImpl(templateRepository,
            ruleRepository, ossStorageAdapter, generator, provisionTxWriter,
            new OssProperties("endpoint", "cn-beijing", "bucket", "ak", "sk", "arn", "sts",
                    "edudoc/uploads", 120, 900));

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private Template published() {
        Template template = new Template();
        template.setId(TPL);
        template.setCourseId(COURSE);
        template.setTemplateName("报告模板");
        template.setOwnerId("T1");
        template.setCurrentVersionId(TPL_VER);
        template.setStatus("P");
        return template;
    }

    @Test
    void studentInCourseProvisionsDocumentBoundToTemplate() {
        AuthContext.set(new UserPrincipal("S1", "s", "S", Set.of("STUDENT"), Set.of(), Set.of(COURSE)));
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(published()));
        when(generator.generate(any(), any())).thenReturn("docx".getBytes());
        when(ossStorageAdapter.putBytes(anyString(), any(), anyString()))
                .thenReturn(new OssStorageAdapter.ObjectInfo(4, "ETAG", null, "SSE-OSS"));
        when(provisionTxWriter.provisionAndBind(any(), any(), anyString(), eq(TPL_VER)))
                .thenReturn(new CreatedDocument("DOC9", "VER9"));

        ProvisionView view = service.provision(TPL, "我的报告", "REPORT");

        assertEquals("DOC9", view.documentId());
        ArgumentCaptor<UploadSession> session = ArgumentCaptor.forClass(UploadSession.class);
        verify(provisionTxWriter).provisionAndBind(session.capture(), any(), anyString(), eq(TPL_VER));
        assertEquals("S1", session.getValue().ownerUserId());
        assertEquals(COURSE, session.getValue().courseId());
    }

    @Test
    void studentOutsideCourseRejected() {
        AuthContext.set(new UserPrincipal("S2", "s", "S", Set.of("STUDENT"), Set.of(), Set.of()));
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(published()));
        var e = assertThrows(CidasBusinessException.class, () -> service.provision(TPL, "报告", "REPORT"));
        assertEquals(40301, e.errorCode().code());
        verify(ossStorageAdapter, never()).putBytes(anyString(), any(), anyString());
    }

    @Test
    void unpublishedTemplateRejected() {
        AuthContext.set(new UserPrincipal("S1", "s", "S", Set.of("STUDENT"), Set.of(), Set.of(COURSE)));
        Template draft = published();
        draft.setStatus("D");
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(draft));
        var e = assertThrows(CidasBusinessException.class, () -> service.provision(TPL, "报告", "REPORT"));
        assertEquals(40901, e.errorCode().code());
    }

    @Test
    void invalidDocTypeRejected() {
        AuthContext.set(new UserPrincipal("S1", "s", "S", Set.of("STUDENT"), Set.of(), Set.of(COURSE)));
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(published()));
        var e = assertThrows(CidasBusinessException.class, () -> service.provision(TPL, "报告", "EXE"));
        assertEquals(40001, e.errorCode().code());
        var e2 = assertThrows(CidasBusinessException.class, () -> service.provision(TPL, " ", "REPORT"));
        assertEquals(40001, e2.errorCode().code());
    }

    @Test
    void duplicateTitleMapsToConflict() {
        AuthContext.set(new UserPrincipal("S1", "s", "S", Set.of("STUDENT"), Set.of(), Set.of(COURSE)));
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(published()));
        when(generator.generate(any(), any())).thenReturn("docx".getBytes());
        when(ossStorageAdapter.putBytes(anyString(), any(), anyString()))
                .thenReturn(new OssStorageAdapter.ObjectInfo(4, "ETAG", null, "SSE-OSS"));
        when(provisionTxWriter.provisionAndBind(any(), any(), anyString(), eq(TPL_VER)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_document_1"));

        var e = assertThrows(CidasBusinessException.class, () -> service.provision(TPL, "同名", "REPORT"));
        assertEquals(40901, e.errorCode().code());
        assertNotNull(e.getMessage());
    }
}
