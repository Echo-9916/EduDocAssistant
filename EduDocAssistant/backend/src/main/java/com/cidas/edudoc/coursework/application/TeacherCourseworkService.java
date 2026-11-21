package com.cidas.edudoc.coursework.application;

import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.coursework.domain.CourseworkRules.RubricItem;
import com.cidas.edudoc.coursework.domain.CourseworkRules.ScoreItem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 教师侧作业域应用服务（06 切片 1）：课程管理、成员导入、作业发布、未提交名单、提交批改与工作台聚合。
 *
 * <p>响应视图一律本接口嵌套 record（照 {@code CourseQueryService.CourseView} 惯例）；
 * 课程范围校验（{@code principal.courseScope()}）与主键格式校验都在实现层完成后才触库。
 */
public interface TeacherCourseworkService {

    /** 教师课程分页列表（本人任教课程，SQL 已分页）。 */
    PageResult<CourseView> pageCourses(CourseQuery query);

    /** 新建课程并把创建者登记为 TEACHER 成员（课程可见性以 course_member 为准）。 */
    CourseView createCourse(CourseCreateCommand command);

    /** 名单导入（同步逐行）：已存在成员计 conflicts，无账号的建未激活账号计 activated。 */
    ImportResult importMembers(String courseId, List<MemberImportRow> rows);

    /** 课程内全部学生（课程管理行展开用，仅本人任教课程）。 */
    List<CourseStudentView> listCourseStudents(String courseId);

    /** 教师作业列表（带提交统计）；courseId 传入时先校验课程范围。 */
    PageResult<AssignmentView> listAssignments(String courseId, String status, Integer page, Integer pageSize);

    /** 新建作业：draft=true 存草稿，否则直接发布；attachmentIds 为发布前已上传的附件。 */
    CreatedAssignment createAssignment(AssignmentCreateCommand command);

    /** 发布前上传作业附件（仅 .docx，与模板要求一致）；返回附件 ID 供发布确认绑定。 */
    AttachmentView uploadAttachment(AttachmentUpload upload);

    /** 未提交学生名单（只读，不投递）。 */
    UnsubmittedResult listUnsubmitted(String assignmentId);

    /** 向未提交学生发布提醒（同一天同一作业按学生去重）。 */
    RemindResult remindUnsubmitted(String assignmentId);

    /** 提交列表（批改视角，仅本人课程内、每人最新一次提交；支持课程/学号姓名/状态筛选）。 */
    PageResult<SubmissionView> listSubmissions(String courseId, String assignmentId, String status,
                                               String keyword, Integer page, Integer pageSize);

    /** 分项批改：按作业评分标准校验并归一化，总分服务端计算。 */
    GradeResult gradeSubmission(String submissionId, GradeCommand command);

    /** 教师工作台聚合：本周截止、待批改、格式问题与优先处理项。 */
    WorkbenchSummary workbenchSummary();

    record CourseQuery(String term, String status, String keyword, Integer page, Integer pageSize) {
    }

    record CourseCreateCommand(String name, String code, String term) {
    }

    record CourseView(String id, String courseCode, String courseName, String termCode, String ownerName,
                      long groups, long students, String templateName, String status, long dueWeek) {
    }

    record MemberImportRow(String accountNo, String name, String group) {
    }

    record CourseStudentView(String userId, String accountNo, String name, String groupName) {
    }

    record ImportResult(int total, int created, int activated, int conflicts, List<ImportFailure> failures) {
    }

    record ImportFailure(String accountNo, String reason) {
    }

    record AssignmentCreateCommand(String courseId, String title, String requirement, String dueTime,
                                   String allowLate, String submissionFormat, String requireFormatCheck,
                                   String templateVersionId, Boolean draft, List<RubricInput> rubric,
                                   List<String> attachmentIds) {
    }

    /** 附件上传入参：控制器读 MultipartFile 后只传字节与元数据，应用层不感知 Spring Web。 */
    record AttachmentUpload(String fileName, String mimeType, long sizeBytes, byte[] bytes) {
    }

    record AttachmentView(String id, String fileName, String mimeType, long sizeBytes) {
    }

    record RubricInput(String name, Integer maxScore) {
    }

    record CreatedAssignment(String id, String status) {
    }

    /** 作业视图：unsubmitted = expected - submitted；rubric 由 rubric_json 解析回分项数组。 */
    record AssignmentView(String id, String courseId, String courseName, String title, String description,
                          OffsetDateTime dueTime, String allowLate, String submissionFormat,
                          String requireFormatCheck, String status, String templateVersionId,
                          String templateName, Integer templateVersionNo,
                          long expected, long submitted, long unsubmitted, long overdue, long graded,
                          long formatIssues, List<RubricItem> rubric) {
    }

    record UnsubmittedResult(List<UnsubmittedStudent> items) {
    }

    record UnsubmittedStudent(String accountNo, String name) {
    }

    record RemindResult(int reminded) {
    }

    record GradeCommand(List<GradeInput> items, String feedback) {
    }

    record GradeInput(String name, Integer score) {
    }

    record GradeResult(double score, String status) {
    }

    /** 提交视图：format 由格式校验计数派生（NONE/BLOCK/WARN/PASS），items 由 score_json 解析。 */
    record SubmissionView(String id, String assignmentId, String assignmentTitle, String courseName,
                          String studentId, String studentName, String accountNo, String documentId,
                          String documentTitle, Integer versionNo, String format, String status,
                          BigDecimal score, String feedback, List<ScoreItem> items, Integer attemptNo,
                          OffsetDateTime submittedAt) {
    }

    /** collabActive 恒 0：协作统计随切片 3 开放，不做假数。 */
    record WorkbenchSummary(long dueThisWeek, long pendingGrade, long formatIssues, long collabActive,
                            List<PriorityView> priority) {
    }

    record PriorityView(String assignmentId, String courseName, String title, OffsetDateTime dueTime,
                        long expected, long submitted, long graded, String remainText) {
    }
}
