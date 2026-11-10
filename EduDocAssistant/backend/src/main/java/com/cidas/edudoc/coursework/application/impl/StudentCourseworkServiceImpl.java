package com.cidas.edudoc.coursework.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.coursework.application.StudentCourseworkService;
import com.cidas.edudoc.coursework.domain.CourseworkRules;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRepository;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.AssignmentStats;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.OwnedDocument;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.StudentAssignment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudentCourseworkServiceImpl implements StudentCourseworkService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final CourseworkRepository courseworkRepository;
    private final AuditService auditService;

    @Override
    public PageResult<StudentAssignmentView> listAssignments(ListQuery query) {
        String studentId = AuthContext.require().userId();
        // HEXTORAW 前在入口拒绝非 hex（同 DocumentQueryServiceImpl 的 1103 审查 S2 口径）
        String courseId = blankToNull(query.courseId());
        if (courseId != null && !Ids.isHex16(courseId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "courseId 格式不合法");
        }
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int pageSize = query.pageSize() == null || query.pageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.pageSize(), MAX_PAGE_SIZE);
        OffsetDateTime now = OffsetDateTime.now();
        String statusFilter = blankToNull(query.status());

        List<StudentAssignmentView> all = courseworkRepository.listStudentAssignments(studentId, courseId).stream()
                .map(row -> toView(row, now))
                // mapper 不做派生状态过滤（PENDING/OVERDUE 由截止时间现算），只能在这里滤
                .filter(view -> statusFilter == null || statusFilter.equals(view.status()))
                .toList();
        // 内存分页：单学生可见作业按课程×学期计只有几十条，SQL 分页留到数据量真上来再加
        // ponytail: 内存分页上限=单学生全部作业行数，量级破千再下沉 SQL（OFFSET/FETCH 见 selectTeacherCourses）
        int from = (int) Math.min((long) (page - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return new PageResult<>(all.subList(from, to), all.size(), page, pageSize);
    }

    @Override
    public SubmissionResult submit(String assignmentId, String documentId) {
        if (!Ids.isHex16(assignmentId) || !Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "ID 格式不合法");
        }
        String studentId = AuthContext.require().userId();
        AssignmentStats assignment = courseworkRepository.findAssignment(assignmentId);
        if (assignment == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        // 鉴权第 4 步（课程范围）：只能提交本人所在课程的作业，否则任意学生可对任意已发布作业提交
        if (!courseworkRepository.isCourseMember(assignment.getCourseId(), studentId)) {
            throw new CidasBusinessException(ErrorCode.FORBIDDEN, "未选修该课程，不能提交此作业");
        }
        // 草稿作业不可提交：列表 SQL 已过滤 DRAFT，但提交端点必须自己拦住（实测 8081 曾直接 201）
        if ("DRAFT".equals(assignment.getStatus())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "作业尚未发布，不能提交");
        }
        // 本人 owner + READY 一条 SQL 同时校验，null 无法区分「无权」与「未解析完成」，统一一句话
        OwnedDocument document = courseworkRepository.findOwnedReadyDocument(studentId, documentId);
        if (document == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在、无权访问或未解析完成");
        }
        CourseworkRules.SubmissionOutcome outcome = CourseworkRules.submissionOutcome(
                assignment.getStatus(), assignment.getDueTime(),
                "Y".equals(assignment.getAllowLate()), OffsetDateTime.now());
        long attemptNo = courseworkRepository.nextAttempt(assignmentId, studentId);
        // format_check_id 传 null：提交前强制校验的同步阻断随校验结果回调/消息切片实现
        //（06 计划：种子作业 require_format_check 为 N，演示不依赖）
        String submissionId = courseworkRepository.createSubmission(assignmentId, studentId, documentId,
                document.getVersionId(), null, attemptNo, outcome.name());
        auditService.record("SUBMIT_ASSIGNMENT", "SUBMISSION", submissionId, documentId, "200",
                Map.of("assignmentId", assignmentId, "attemptNo", attemptNo, "status", outcome.name()));
        return new SubmissionResult(submissionId, attemptNo, outcome.name());
    }

    private static StudentAssignmentView toView(StudentAssignment row, OffsetDateTime now) {
        String status = CourseworkRules.studentStatus(row.getSubmissionStatus(), row.getDueTime(), now);
        return new StudentAssignmentView(
                row.getAssignmentId(), row.getCourseName(), row.getTitle(), row.getDescription(),
                iso(row.getDueTime()), remainText(status, row.getDueTime(), now), status,
                formatView(row.getBlockingCount(), row.getWarningCount()),
                row.getScore(), row.getFeedback(), row.getAttemptNo(), row.getDocumentTitle(),
                iso(row.getSubmittedAt()), row.getDocumentId(), row.getDocumentVersionId(),
                row.getVersionNo(), row.getSubmissionFormat(), row.getRequireFormatCheck());
    }

    /** 格式结论派生：-1 表示未跑校验（NONE），阻塞优先于提醒。 */
    private static FormatView formatView(long blockingCount, long warningCount) {
        if (blockingCount < 0) {
            return new FormatView("NONE", "未做格式校验");
        }
        if (blockingCount > 0) {
            return new FormatView("BLOCK", blockingCount + " 项阻塞：需先修复再提交");
        }
        if (warningCount > 0) {
            return new FormatView("WARN", warningCount + " 项提醒：建议修复后再提交");
        }
        return new FormatView("PASS", "格式校验通过");
    }

    /** 剩余时间文案：只算整天数，不做时区换算（库列自带偏移，now 亦为 OffsetDateTime）。 */
    private static String remainText(String status, OffsetDateTime dueTime, OffsetDateTime now) {
        if (CourseworkRules.SUBMITTED.equals(status) || CourseworkRules.GRADED.equals(status) || dueTime == null) {
            return "";
        }
        if (CourseworkRules.OVERDUE.equals(status)) {
            long days = ChronoUnit.DAYS.between(dueTime, now);
            return days >= 1 ? "已逾期 " + days + " 天" : "已逾期不足 1 天";
        }
        long days = ChronoUnit.DAYS.between(now, dueTime);
        return days <= 0 ? "今天截止" : days + " 天后截止";
    }

    private static String iso(OffsetDateTime time) {
        return time == null ? null : time.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
