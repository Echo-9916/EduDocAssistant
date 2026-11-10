package com.cidas.edudoc.coursework.application;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.coursework.application.StudentCourseworkService.ListQuery;
import com.cidas.edudoc.coursework.application.StudentCourseworkService.StudentAssignmentView;
import com.cidas.edudoc.coursework.application.StudentCourseworkService.SubmissionResult;
import com.cidas.edudoc.coursework.application.impl.StudentCourseworkServiceImpl;
import com.cidas.edudoc.coursework.infrastructure.CourseworkMapper;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRepository;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学生作业列表（四态派生 + 过滤 + 内存分页）与提交（次数递增、非本人文档、关闭、超期禁迟交）。
 * 仓储用真实现 + mock Mapper，让「attemptNo=已有提交数+1」走真实的 nextAttempt 计算。
 */
class StudentCourseworkServiceTest {

    private static final String STUDENT_ID = "AA0000000000000000000000000000F1";
    private static final String ASSIGNMENT_ID = "AA0000000000000000000000000000B1";
    private static final String DOCUMENT_ID = "AA0000000000000000000000000000A1";
    private static final String VERSION_ID = "AA0000000000000000000000000000C1";
    private static final String SUBMISSION_ID = "AA0000000000000000000000000000D1";
    private static final String COURSE_ID = "AA0000000000000000000000000000E1";

    private final CourseworkMapper mapper = mock(CourseworkMapper.class);
    private final AuditService auditService = mock(AuditService.class);
    private final StudentCourseworkService service =
            new StudentCourseworkServiceImpl(new CourseworkRepository(mapper), auditService);

    @BeforeEach
    void login() {
        AuthContext.set(new UserPrincipal(STUDENT_ID, "2023001", "张同学", Set.of("STUDENT"),
                Set.of("SUBMISSION_SELF_READ", "DOCUMENT_CREATE"), Set.of()));
        // 提交鉴权第 4 步（课程范围）默认放行；未选修的用例单独把这里改成 0
        when(mapper.countCourseMember(COURSE_ID, STUDENT_ID)).thenReturn(1L);
    }

    @Test
    void submitRejectsAssignmentOfCourseStudentNotEnrolledIn() {
        when(mapper.selectAssignmentById(ASSIGNMENT_ID))
                .thenReturn(assignment("PUBLISHED", OffsetDateTime.now().plusDays(2), "N"));
        when(mapper.countCourseMember(COURSE_ID, STUDENT_ID)).thenReturn(0L);

        CidasBusinessException ex = assertThrows(CidasBusinessException.class,
                () -> service.submit(ASSIGNMENT_ID, DOCUMENT_ID));

        assertEquals(ErrorCode.FORBIDDEN, ex.errorCode());
        verify(mapper, never()).insertSubmission(any(), any(), any(), any(), any(), anyLong(), any());
    }

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    // ---------- 提交 ----------

    @Test
    void submitSucceedsAndAttemptNoIsExistingCountPlusOne() {
        OffsetDateTime due = OffsetDateTime.now().plusDays(2);
        when(mapper.selectAssignmentById(ASSIGNMENT_ID)).thenReturn(assignment("PUBLISHED", due, "N"));
        when(mapper.selectOwnedReadyDocument(STUDENT_ID, DOCUMENT_ID)).thenReturn(ownedDocument());
        when(mapper.countSubmissions(ASSIGNMENT_ID, STUDENT_ID)).thenReturn(1L);
        when(mapper.selectSubmissionId(ASSIGNMENT_ID, STUDENT_ID, 2L)).thenReturn(SUBMISSION_ID);

        SubmissionResult result = service.submit(ASSIGNMENT_ID, DOCUMENT_ID);

        assertEquals(2L, result.attemptNo());
        assertEquals("SUBMITTED", result.status());
        assertEquals(SUBMISSION_ID, result.submissionId());
        // format_check_id 本期传 null（提交前强制校验随消息切片）
        verify(mapper).insertSubmission(ASSIGNMENT_ID, STUDENT_ID, DOCUMENT_ID, VERSION_ID, null, 2L, "SUBMITTED");
        verify(auditService).record(eq("SUBMIT_ASSIGNMENT"), eq("SUBMISSION"), eq(SUBMISSION_ID),
                eq(DOCUMENT_ID), eq("200"), any());
    }

    @Test
    void submitRejectsDocumentNotOwnedOrNotReady() {
        when(mapper.selectAssignmentById(ASSIGNMENT_ID))
                .thenReturn(assignment("PUBLISHED", OffsetDateTime.now().plusDays(2), "N"));
        when(mapper.selectOwnedReadyDocument(STUDENT_ID, DOCUMENT_ID)).thenReturn(null);

        CidasBusinessException ex = assertThrows(CidasBusinessException.class,
                () -> service.submit(ASSIGNMENT_ID, DOCUMENT_ID));

        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode());
        verify(mapper, never()).insertSubmission(any(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void submitRejectsClosedAssignment() {
        when(mapper.selectAssignmentById(ASSIGNMENT_ID))
                .thenReturn(assignment("CLOSED", OffsetDateTime.now().plusDays(2), "Y"));
        when(mapper.selectOwnedReadyDocument(STUDENT_ID, DOCUMENT_ID)).thenReturn(ownedDocument());

        CidasBusinessException ex = assertThrows(CidasBusinessException.class,
                () -> service.submit(ASSIGNMENT_ID, DOCUMENT_ID));

        assertEquals(ErrorCode.CONFLICT, ex.errorCode());
        verify(mapper, never()).insertSubmission(any(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void submitRejectsOverdueWhenLateNotAllowed() {
        when(mapper.selectAssignmentById(ASSIGNMENT_ID))
                .thenReturn(assignment("PUBLISHED", OffsetDateTime.now().minusDays(1), "N"));
        when(mapper.selectOwnedReadyDocument(STUDENT_ID, DOCUMENT_ID)).thenReturn(ownedDocument());

        CidasBusinessException ex = assertThrows(CidasBusinessException.class,
                () -> service.submit(ASSIGNMENT_ID, DOCUMENT_ID));

        assertEquals(ErrorCode.CONFLICT, ex.errorCode());
        verify(mapper, never()).insertSubmission(any(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void submitRejectsMalformedIds() {
        CidasBusinessException ex = assertThrows(CidasBusinessException.class,
                () -> service.submit("not-hex", DOCUMENT_ID));
        assertEquals(ErrorCode.BAD_REQUEST, ex.errorCode());
    }

    // ---------- 列表 ----------

    @Test
    void listDerivesFourStudentStatuses() {
        OffsetDateTime now = OffsetDateTime.now();
        when(mapper.selectStudentAssignments(eq(STUDENT_ID), isNull())).thenReturn(List.of(
                studentRow("A1", null, now.plusDays(3).plusHours(1), -1, -1),
                studentRow("A2", "SUBMITTED", now.plusDays(3), 3, 0),
                studentRow("A3", "GRADED", now.plusDays(3), 0, 2),
                studentRow("A4", null, now.minusDays(2).minusHours(1), 0, 0),
                studentRow("A5", null, now.plusHours(5), -1, -1)));

        List<StudentAssignmentView> items = service.listAssignments(new ListQuery(null, null, null, null)).items();

        assertEquals(5, items.size());
        assertEquals("PENDING", items.get(0).status());
        assertEquals("3 天后截止", items.get(0).remainText());
        assertEquals("NONE", items.get(0).format().state());
        assertEquals("未做格式校验", items.get(0).format().note());
        assertEquals("SUBMITTED", items.get(1).status());
        assertEquals("", items.get(1).remainText());
        assertEquals("BLOCK", items.get(1).format().state());
        assertTrue(items.get(1).format().note().contains("3 项阻塞"));
        assertEquals("GRADED", items.get(2).status());
        assertEquals("WARN", items.get(2).format().state());
        assertEquals("OVERDUE", items.get(3).status());
        assertEquals("已逾期 2 天", items.get(3).remainText());
        assertEquals("PASS", items.get(3).format().state());
        assertEquals("今天截止", items.get(4).remainText());
    }

    @Test
    void listFiltersByDerivedOverdueStatus() {
        OffsetDateTime now = OffsetDateTime.now();
        when(mapper.selectStudentAssignments(eq(STUDENT_ID), isNull())).thenReturn(List.of(
                studentRow("A1", null, now.plusDays(3), -1, -1),
                studentRow("A4", null, now.minusDays(2).minusHours(1), -1, -1)));

        PageResult<StudentAssignmentView> result =
                service.listAssignments(new ListQuery("OVERDUE", null, null, null));

        assertEquals(1, result.total());
        assertEquals(1, result.items().size());
        assertEquals("A4", result.items().get(0).id());
    }

    @Test
    void listReturnsEmptyItemsNotNull() {
        when(mapper.selectStudentAssignments(eq(STUDENT_ID), isNull())).thenReturn(List.of());

        PageResult<StudentAssignmentView> result =
                service.listAssignments(new ListQuery(null, null, null, null));

        assertNotNull(result.items());
        assertTrue(result.items().isEmpty());
        assertEquals(0, result.total());
    }

    @Test
    void listRejectsMalformedCourseId() {
        CidasBusinessException ex = assertThrows(CidasBusinessException.class,
                () -> service.listAssignments(new ListQuery(null, "not-hex", null, null)));
        assertEquals(ErrorCode.BAD_REQUEST, ex.errorCode());
    }

    // ---------- 构造辅助 ----------

    private static CourseworkRow.AssignmentStats assignment(String status, OffsetDateTime dueTime, String allowLate) {
        CourseworkRow.AssignmentStats row = new CourseworkRow.AssignmentStats();
        row.setId(ASSIGNMENT_ID);
        row.setCourseId(COURSE_ID);
        row.setStatus(status);
        row.setDueTime(dueTime);
        row.setAllowLate(allowLate);
        return row;
    }

    private static CourseworkRow.OwnedDocument ownedDocument() {
        CourseworkRow.OwnedDocument doc = new CourseworkRow.OwnedDocument();
        doc.setDocumentId(DOCUMENT_ID);
        doc.setTitle("实验报告.docx");
        doc.setVersionId(VERSION_ID);
        doc.setVersionNo(2);
        doc.setStatus("READY");
        return doc;
    }

    private static CourseworkRow.StudentAssignment studentRow(String id, String submissionStatus,
                                                              OffsetDateTime dueTime, long blocking, long warning) {
        CourseworkRow.StudentAssignment row = new CourseworkRow.StudentAssignment();
        row.setAssignmentId(id);
        row.setCourseName("数据库原理");
        row.setTitle("作业 " + id);
        row.setDescription("按要求完成报告");
        row.setDueTime(dueTime);
        row.setSubmissionStatus(submissionStatus);
        row.setBlockingCount(blocking);
        row.setWarningCount(warning);
        row.setSubmissionFormat("DOCX");
        row.setRequireFormatCheck("N");
        if ("GRADED".equals(submissionStatus)) {
            row.setScore(new BigDecimal("85"));
            row.setFeedback("结构完整");
            row.setAttemptNo(1);
            row.setDocumentTitle("实验报告.docx");
            row.setSubmittedAt(OffsetDateTime.now().minusDays(1));
        }
        return row;
    }
}
