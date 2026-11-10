package com.cidas.edudoc.coursework.application;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.AssignmentCreateCommand;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.CreatedAssignment;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.GradeCommand;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.GradeInput;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.GradeResult;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.ImportResult;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.MemberImportRow;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.RubricInput;
import com.cidas.edudoc.coursework.application.impl.TeacherCourseworkServiceImpl;
import com.cidas.edudoc.coursework.domain.CourseworkRules.RubricItem;
import com.cidas.edudoc.coursework.infrastructure.CourseworkMapper;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRepository;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRepository.AssignmentCreate;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.AssignmentStats;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.MemberAccount;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.Submission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
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

/** 教师侧作业域服务：发布校验顺序、批改归一化与总分口径、名单导入四类行计数。 */
class TeacherCourseworkServiceTest {

    private static final String HEX_TEACHER = "C".repeat(32);
    private static final String HEX_COURSE = "A".repeat(32);
    private static final String HEX_OTHER_COURSE = "B".repeat(32);
    private static final String HEX_ASSIGNMENT = "D".repeat(32);
    private static final String HEX_SUBMISSION = "E".repeat(32);
    private static final String RUBRIC_JSON = "[{\"name\":\"内容\",\"maxScore\":60},{\"name\":\"格式\",\"maxScore\":40}]";

    private final CourseworkRepository repository = mock(CourseworkRepository.class);
    private final CourseworkMapper mapper = mock(CourseworkMapper.class);
    private final AuditService auditService = mock(AuditService.class);
    private final TeacherCourseworkService service =
            new TeacherCourseworkServiceImpl(repository, mapper, auditService);

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    /** 登录教师，课程范围默认含 HEX_COURSE。 */
    private static void teacher(String... courseScope) {
        AuthContext.set(new UserPrincipal(HEX_TEACHER, "teacher", "李老师", Set.of("TEACHER"),
                Set.of("ASSIGNMENT_MANAGE", "SUBMISSION_READ"), Set.of(courseScope)));
    }

    private static AssignmentCreateCommand publishCommand(String courseId, List<RubricInput> rubric) {
        return new AssignmentCreateCommand(courseId, "课程设计报告", "要求见文档",
                "2026-11-20T23:59:00+08:00", "Y", "DOCX", "N", false, rubric);
    }

    private static AssignmentStats assignmentRow() {
        AssignmentStats row = new AssignmentStats();
        row.setId(HEX_ASSIGNMENT);
        row.setCourseId(HEX_COURSE);
        row.setRubricJson(RUBRIC_JSON);
        return row;
    }

    private static Submission submissionRow() {
        Submission row = new Submission();
        row.setId(HEX_SUBMISSION);
        row.setAssignmentId(HEX_ASSIGNMENT);
        return row;
    }

    // ---------- 作业发布 ----------

    @Test
    void publishHappyPathSerializesRubricAndAudits() {
        teacher(HEX_COURSE);
        when(repository.createAssignment(any())).thenReturn(HEX_ASSIGNMENT);

        CreatedAssignment result = service.createAssignment(publishCommand(HEX_COURSE,
                List.of(new RubricInput("内容", 60), new RubricInput("格式", 40))));

        assertEquals(HEX_ASSIGNMENT, result.id());
        assertEquals("PUBLISHED", result.status());
        ArgumentCaptor<AssignmentCreate> captor = ArgumentCaptor.forClass(AssignmentCreate.class);
        verify(repository).createAssignment(captor.capture());
        AssignmentCreate saved = captor.getValue();
        assertEquals("PUBLISHED", saved.status());
        assertEquals(HEX_TEACHER, saved.publishedByHex());
        // rubric_json 必须能被 CourseworkJson.readRubric 原样读回（发布/批改往返闭环）
        List<RubricItem> rubric = CourseworkJson.readRubric(saved.rubricJson());
        assertEquals(List.of(new RubricItem("内容", 60), new RubricItem("格式", 40)), rubric);
        verify(auditService).record(eq("ASSIGNMENT_CREATED"), eq("ASSIGNMENT"), eq(HEX_ASSIGNMENT),
                any(), eq("200"), any());
    }

    @Test
    void publishRejectsEmptyRubric() {
        teacher(HEX_COURSE);

        var e = assertThrows(CidasBusinessException.class,
                () -> service.createAssignment(publishCommand(HEX_COURSE, List.of())));

        assertEquals(40001, e.errorCode().code());
        verify(repository, never()).createAssignment(any());
    }

    @Test
    void publishOutsideCourseScopeDenied() {
        teacher(HEX_COURSE);

        var e = assertThrows(CidasBusinessException.class,
                () -> service.createAssignment(publishCommand(HEX_OTHER_COURSE,
                        List.of(new RubricInput("内容", 60)))));

        assertEquals(40301, e.errorCode().code());
        verify(repository, never()).createAssignment(any());
    }

    // ---------- 批改 ----------

    @Test
    void gradeRejectsScoreOverMaxAndNeverWrites() {
        teacher(HEX_COURSE);
        when(mapper.selectSubmissionById(HEX_SUBMISSION)).thenReturn(submissionRow());
        when(repository.findAssignment(HEX_ASSIGNMENT)).thenReturn(assignmentRow());

        var e = assertThrows(CidasBusinessException.class, () -> service.gradeSubmission(HEX_SUBMISSION,
                new GradeCommand(List.of(new GradeInput("内容", 61), new GradeInput("格式", 30)), "超分")));

        assertEquals(40001, e.errorCode().code());
        verify(repository, never()).gradeSubmission(anyString(), any(), any(), any(), any());
    }

    @Test
    void gradeWritesNormalizedJsonAndServerSideTotal() {
        teacher(HEX_COURSE);
        when(mapper.selectSubmissionById(HEX_SUBMISSION)).thenReturn(submissionRow());
        when(repository.findAssignment(HEX_ASSIGNMENT)).thenReturn(assignmentRow());

        // 入参故意乱序且不带 maxScore：归一化与总分必须由服务端按评分标准计算
        GradeResult result = service.gradeSubmission(HEX_SUBMISSION, new GradeCommand(
                List.of(new GradeInput("格式", 30), new GradeInput("内容", 50)), "做得不错"));

        assertEquals(80.0, result.score());
        assertEquals("GRADED", result.status());
        ArgumentCaptor<BigDecimal> scoreCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).gradeSubmission(eq(HEX_SUBMISSION), scoreCaptor.capture(), eq("做得不错"),
                jsonCaptor.capture(), eq("GRADED"));
        assertEquals(0, scoreCaptor.getValue().compareTo(BigDecimal.valueOf(80)));
        assertEquals("[{\"name\":\"内容\",\"maxScore\":60,\"score\":50},"
                + "{\"name\":\"格式\",\"maxScore\":40,\"score\":30}]", jsonCaptor.getValue());
        verify(auditService).record(eq("SUBMISSION_GRADED"), eq("SUBMISSION"), eq(HEX_SUBMISSION),
                any(), eq("200"), any());
    }

    // ---------- 名单导入 ----------

    @Test
    void memberImportCountsAllFourRowKinds() {
        teacher(HEX_COURSE);
        MemberAccount member = new MemberAccount();
        member.setUserId("F".repeat(32));
        when(repository.findUserByAccount("2023001")).thenReturn(member);
        when(repository.isCourseMember(HEX_COURSE, member.getUserId())).thenReturn(true);
        MemberAccount transfer = new MemberAccount();
        transfer.setUserId("1".repeat(32));
        when(repository.findUserByAccount("2023002")).thenReturn(transfer);
        when(repository.isCourseMember(HEX_COURSE, transfer.getUserId())).thenReturn(false);
        when(repository.findUserByAccount("2023003")).thenReturn(null);
        when(repository.createInactiveUser("2023003", "王同学")).thenReturn("2".repeat(32));

        ImportResult result = service.importMembers(HEX_COURSE, List.of(
                new MemberImportRow("2023001", "张同学", "A组"),   // 已是成员 → conflicts
                new MemberImportRow("2023002", "李同学", "A组"),   // 已存在非成员 → created
                new MemberImportRow("2023003", "王同学", "B组"),   // 无账号 → 建未激活账号 activated
                new MemberImportRow("  ", "缺学号", null)));       // 学号为空 → failures

        assertEquals(4, result.total());
        assertEquals(1, result.created());
        assertEquals(1, result.activated());
        assertEquals(1, result.conflicts());
        assertEquals(1, result.failures().size());
        verify(repository).addMember(HEX_COURSE, "1".repeat(32), "STUDENT", "A组");
        verify(repository).addMember(HEX_COURSE, "2".repeat(32), "STUDENT", "B组");
        verify(repository, never()).addMember(HEX_COURSE, member.getUserId(), "STUDENT", null);
        verify(auditService).record(eq("MEMBER_IMPORTED"), eq("COURSE"), eq(HEX_COURSE),
                any(), eq("200"), any());
    }
}
