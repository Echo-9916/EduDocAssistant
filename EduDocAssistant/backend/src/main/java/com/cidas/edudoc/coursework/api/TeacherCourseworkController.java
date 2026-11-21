package com.cidas.edudoc.coursework.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.AssignmentCreateCommand;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.AssignmentView;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.AttachmentUpload;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.AttachmentView;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.CourseCreateCommand;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.CourseQuery;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.CourseStudentView;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.CourseView;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.CreatedAssignment;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.GradeCommand;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.GradeInput;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.GradeResult;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.ImportResult;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.MemberImportRow;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.RemindResult;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.RubricInput;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.SubmissionView;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.UnsubmittedResult;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService.WorkbenchSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 教师侧作业域端点（06 切片 1）：课程管理、名单导入、作业发布、未提交名单、提交批改与工作台聚合。
 * 本类只做请求体到应用服务命令的装配与 {@code ApiResponse} 封装，校验全部在服务层。
 */
@RestController
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
public class TeacherCourseworkController {

    private final TeacherCourseworkService teacherCourseworkService;

    @GetMapping("/courses")
    @RequirePermission(PermissionCode.ASSIGNMENT_MANAGE)
    public ApiResponse<PageResult<CourseView>> courses(
            @RequestParam(name = "term", required = false) String term,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ApiResponse.ok(teacherCourseworkService.pageCourses(
                new CourseQuery(term, status, keyword, page, pageSize)));
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.ASSIGNMENT_MANAGE)
    public ApiResponse<CourseView> createCourse(@RequestBody CourseCreateRequest request) {
        return ApiResponse.ok(teacherCourseworkService.createCourse(
                new CourseCreateCommand(request.name(), request.code(), request.term())));
    }

    @PostMapping("/courses/{courseId}/member-imports")
    @RequirePermission(PermissionCode.ASSIGNMENT_MANAGE)
    public ApiResponse<ImportResult> importMembers(@PathVariable("courseId") String courseId,
                                                   @RequestBody MemberImportRequest request) {
        List<MemberImportRow> rows = request.rows() == null ? List.of()
                : request.rows().stream()
                        .map(row -> new MemberImportRow(row.accountNo(), row.name(), row.group()))
                        .toList();
        return ApiResponse.ok(teacherCourseworkService.importMembers(courseId, rows));
    }

    /** 课程管理行展开：该课程全部活跃学生。 */
    @GetMapping("/courses/{courseId}/students")
    @RequirePermission(PermissionCode.ASSIGNMENT_MANAGE)
    public ApiResponse<List<CourseStudentView>> courseStudents(@PathVariable("courseId") String courseId) {
        return ApiResponse.ok(teacherCourseworkService.listCourseStudents(courseId));
    }

    @GetMapping("/assignments")
    @RequirePermission(PermissionCode.ASSIGNMENT_MANAGE)
    public ApiResponse<PageResult<AssignmentView>> assignments(
            @RequestParam(name = "courseId", required = false) String courseId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ApiResponse.ok(teacherCourseworkService.listAssignments(courseId, status, page, pageSize));
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.ASSIGNMENT_MANAGE)
    public ApiResponse<CreatedAssignment> createAssignment(@RequestBody AssignmentCreateRequest request) {
        List<RubricInput> rubric = request.rubric() == null ? List.of()
                : request.rubric().stream()
                        .map(item -> new RubricInput(item.name(), item.maxScore()))
                        .toList();
        return ApiResponse.ok(teacherCourseworkService.createAssignment(new AssignmentCreateCommand(
                request.courseId(), request.title(), request.requirement(), request.dueTime(),
                request.allowLate(), request.submissionFormat(), request.requireFormatCheck(),
                request.templateVersionId(), request.draft(), rubric, request.attachmentIds())));
    }

    /** 发布作业携带附件：先上传拿到 attachmentIds，发布确认时随 @PostMapping("/assignments") 绑定。 */
    @PostMapping(value = "/assignment-attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(PermissionCode.ASSIGNMENT_MANAGE)
    public ApiResponse<AttachmentView> uploadAttachment(@RequestParam("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "附件读取失败");
        }
        return ApiResponse.ok(teacherCourseworkService.uploadAttachment(
                new AttachmentUpload(file.getOriginalFilename(), file.getContentType(), file.getSize(), bytes)));
    }

    @GetMapping("/assignments/{assignmentId}/unsubmitted")
    @RequirePermission(PermissionCode.ASSIGNMENT_MANAGE)
    public ApiResponse<UnsubmittedResult> unsubmitted(@PathVariable("assignmentId") String assignmentId) {
        return ApiResponse.ok(teacherCourseworkService.listUnsubmitted(assignmentId));
    }

    @GetMapping("/submissions")
    @RequirePermission(PermissionCode.SUBMISSION_READ)
    public ApiResponse<PageResult<SubmissionView>> submissions(
            @RequestParam(name = "courseId", required = false) String courseId,
            @RequestParam(name = "assignmentId", required = false) String assignmentId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ApiResponse.ok(teacherCourseworkService.listSubmissions(
                courseId, assignmentId, status, keyword, page, pageSize));
    }

    /** 向未提交学生发布提醒：写通知 outbox + 审计，同一天同一作业按学生去重。 */
    @PostMapping("/assignments/{assignmentId}/reminders")
    @RequirePermission(PermissionCode.ASSIGNMENT_MANAGE)
    public ApiResponse<RemindResult> remind(@PathVariable("assignmentId") String assignmentId) {
        return ApiResponse.ok(teacherCourseworkService.remindUnsubmitted(assignmentId));
    }

    @PutMapping("/submissions/{submissionId}/grade")
    @RequirePermission(PermissionCode.ASSIGNMENT_MANAGE)
    public ApiResponse<GradeResult> grade(@PathVariable("submissionId") String submissionId,
                                          @RequestBody GradeRequest request) {
        List<GradeInput> items = request.items() == null ? List.of()
                : request.items().stream()
                        .map(item -> new GradeInput(item.name(), item.score()))
                        .toList();
        return ApiResponse.ok(teacherCourseworkService.gradeSubmission(submissionId,
                new GradeCommand(items, request.feedback())));
    }

    @GetMapping("/workbench-summary")
    @RequirePermission(PermissionCode.ASSIGNMENT_MANAGE)
    public ApiResponse<WorkbenchSummary> workbenchSummary() {
        return ApiResponse.ok(teacherCourseworkService.workbenchSummary());
    }

    // ---------- 请求体（仅本 Controller 使用，不另开 dto 包） ----------

    /** 新建课程请求体。 */
    public record CourseCreateRequest(String name, String code, String term) {
    }

    /** 名单导入请求体：学号即登录名（username）。 */
    public record MemberImportRequest(List<MemberImportRowRequest> rows) {
    }

    public record MemberImportRowRequest(String accountNo, String name, String group) {
    }

    /** 新建作业请求体：draft=true 存草稿，否则直接发布；dueTime 为 ISO-8601 带时区偏移串。 */
    public record AssignmentCreateRequest(String courseId, String title, String requirement, String dueTime,
                                          String allowLate, String submissionFormat, String requireFormatCheck,
                                          String templateVersionId, Boolean draft, List<RubricInputRequest> rubric,
                                          List<String> attachmentIds) {
    }

    public record RubricInputRequest(String name, Integer maxScore) {
    }

    /** 批改请求体：分项得分 + 反馈；总分由服务端按评分标准计算，不接收前端总分。 */
    public record GradeRequest(List<GradeItemRequest> items, String feedback) {
    }

    public record GradeItemRequest(String name, Integer score) {
    }
}
