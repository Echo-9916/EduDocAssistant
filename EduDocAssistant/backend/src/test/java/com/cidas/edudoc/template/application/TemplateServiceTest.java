package com.cidas.edudoc.template.application;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.template.application.TemplateService.CreateCommand;
import com.cidas.edudoc.template.application.impl.TemplateServiceImpl;
import com.cidas.edudoc.template.application.TemplateService.TemplateView;
import com.cidas.edudoc.template.infrastructure.Template;
import com.cidas.edudoc.template.infrastructure.TemplateRepository;
import com.cidas.edudoc.template.infrastructure.TemplateViewRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-01 模板主档：三角色可见性矩阵、同名冲突、状态迁移门、草稿删除门。 */
class TemplateServiceTest {

    private static final String HEX_COURSE = "A1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String HEX_OTHER_COURSE = "B1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String HEX_TPL = "C1B2C3D4E5F60708090A0B0C0D0E0F10";

    private final TemplateRepository repository = mock(TemplateRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final TemplateService service = new TemplateServiceImpl(repository, auditService);

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private static void principal(String userId, String role, List<String> courseScope) {
        AuthContext.set(new UserPrincipal(userId, "u", "U", Set.of(role), Set.of(), Set.copyOf(courseScope)));
    }

    private static TemplateViewRow row(String status, String currentVersionId) {
        TemplateViewRow r = new TemplateViewRow();
        r.setId(HEX_TPL);
        r.setCourseId(HEX_COURSE);
        r.setCourseName("Web 开发");
        r.setTemplateName("报告模板");
        r.setOwnerId("OWNER10000000000000000000000000000000000A");
        r.setStatus(status);
        r.setCurrentVersionId(currentVersionId);
        return r;
    }

    @Test
    void studentListForcesPublishedMode() {
        principal("S1", "STUDENT", List.of(HEX_COURSE));
        when(repository.count(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(1L);
        when(repository.page(any(), any(), any(), any(), any(), anyBoolean(), anyLong(), anyInt()))
                .thenReturn(List.of(row("P", null)));

        PageResult<TemplateView> result = service.list(new TemplateService.ListQuery(HEX_COURSE, "D", 1, 10));

        assertEquals(1, result.total());
        assertEquals("P", result.items().get(0).status());
        // 学生请求 status=D：可见性模式必须仍强制「已发布」
        verify(repository).page(eq("STUDENT"), any(), eq(List.of(HEX_COURSE)), eq(HEX_COURSE), eq("P"),
                eq(false), eq(0L), eq(10));
    }

    @Test
    void teacherListSeesOwnedOrScopedCourses() {
        principal("T1", "TEACHER", List.of(HEX_COURSE));
        when(repository.count(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(1L);

        service.list(new TemplateService.ListQuery(null, null, null, null));

        verify(repository).page(eq("TEACHER"), eq("T1"), eq(List.of(HEX_COURSE)), any(), any(),
                eq(false), anyLong(), anyInt());
    }

    @Test
    void opsListSeesEverything() {
        principal("O1", "OPS", List.of());
        when(repository.count(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(1L);

        service.list(new TemplateService.ListQuery(null, null, null, null));

        verify(repository).page(eq("OPS"), any(), any(), any(), any(), eq(true), anyLong(), anyInt());
    }

    @Test
    void invalidCourseIdFormatRejected() {
        principal("T1", "TEACHER", List.of());
        var e = assertThrows(CidasBusinessException.class,
                () -> service.create(new CreateCommand("not-hex", "模板", null)));
        assertEquals(40001, e.errorCode().code());
    }

    @Test
    void teacherCreatingOutsideOwnCourseDenied() {
        principal("T1", "TEACHER", List.of(HEX_COURSE));
        var e = assertThrows(CidasBusinessException.class,
                () -> service.create(new CreateCommand(HEX_OTHER_COURSE, "模板", null)));
        assertEquals(40301, e.errorCode().code());
        verify(repository, never()).insert(any());
    }

    @Test
    void duplicateNameMapsToConflict() {
        principal("T1", "TEACHER", List.of(HEX_COURSE));
        when(repository.insert(any())).thenThrow(new DuplicateKeyException("uk_template_1"));
        var e = assertThrows(CidasBusinessException.class,
                () -> service.create(new CreateCommand(HEX_COURSE, "报告模板", null)));
        assertEquals(40901, e.errorCode().code());
    }

    @Test
    void createPersistsDraftAndAudits() {
        principal("T1", "TEACHER", List.of(HEX_COURSE));
        when(repository.insert(any())).thenReturn(1);
        when(repository.findById(anyString())).thenReturn(Optional.of(
                new Template(HEX_TPL, HEX_COURSE, "报告模板", null, "T1", null, "D")));

        TemplateView view = service.create(new CreateCommand(HEX_COURSE, "报告模板", "v1 要求"));

        ArgumentCaptor<Template> captor = ArgumentCaptor.forClass(Template.class);
        verify(repository).insert(captor.capture());
        assertEquals("D", captor.getValue().getStatus());
        assertEquals("T1", captor.getValue().getOwnerId());
        assertEquals("D", view.status());
        verify(auditService).record(eq("TEMPLATE_CREATED"), eq("TEMPLATE"), eq(captor.getValue().getId()),
                any(), eq("200"), any());
    }

    @Test
    void publishStatusRequiresCurrentVersion() {
        principal("T1", "TEACHER", List.of());
        when(repository.findById(HEX_TPL)).thenReturn(Optional.of(
                new Template(HEX_TPL, HEX_COURSE, "报告模板", null, "T1", null, "D")));
        var e = assertThrows(CidasBusinessException.class, () -> service.changeStatus(HEX_TPL, "P"));
        assertEquals(40901, e.errorCode().code());
        verify(repository, never()).updateStatus(any(), any());
    }

    @Test
    void deactivateAllowedAndAudited() {
        principal("T1", "TEACHER", List.of());
        when(repository.findById(HEX_TPL)).thenReturn(Optional.of(
                new Template(HEX_TPL, HEX_COURSE, "报告模板", null, "T1", HEX_TPL, "P")));

        service.changeStatus(HEX_TPL, "A");

        verify(repository).updateStatus(HEX_TPL, "A");
        verify(auditService).record(eq("TEMPLATE_STATUS_CHANGED"), eq("TEMPLATE"), eq(HEX_TPL), any(),
                eq("200"), any());
    }

    @Test
    void illegalStatusValueRejected() {
        principal("T1", "TEACHER", List.of());
        var e = assertThrows(CidasBusinessException.class, () -> service.changeStatus(HEX_TPL, "X"));
        assertEquals(40001, e.errorCode().code());
    }

    @Test
    void deleteOnlyWhenGuardedSqlMatchesDraftWithoutVersions() {
        principal("T1", "TEACHER", List.of());
        when(repository.findById(HEX_TPL)).thenReturn(Optional.of(
                new Template(HEX_TPL, HEX_COURSE, "报告模板", null, "T1", null, "D")));
        when(repository.deleteDraft(HEX_TPL)).thenReturn(0);
        var e = assertThrows(CidasBusinessException.class, () -> service.deleteDraft(HEX_TPL));
        assertEquals(40901, e.errorCode().code());

        when(repository.deleteDraft(HEX_TPL)).thenReturn(1);
        service.deleteDraft(HEX_TPL);
        verify(auditService).record(eq("TEMPLATE_DELETED"), eq("TEMPLATE"), eq(HEX_TPL), any(), eq("200"), any());
    }

    @Test
    void invisibleTemplateDeniedForNonOwnerTeacher() {
        principal("T2", "TEACHER", List.of(HEX_OTHER_COURSE));
        when(repository.findById(HEX_TPL)).thenReturn(Optional.of(
                new Template(HEX_TPL, HEX_COURSE, "报告模板", null, "T1", null, "D")));
        var e = assertThrows(CidasBusinessException.class, () -> service.deleteDraft(HEX_TPL));
        assertEquals(40401, e.errorCode().code());
    }

}
