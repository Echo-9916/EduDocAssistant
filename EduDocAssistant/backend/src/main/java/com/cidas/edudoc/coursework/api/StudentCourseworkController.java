package com.cidas.edudoc.coursework.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.coursework.application.StudentCourseworkService;
import com.cidas.edudoc.coursework.application.StudentCourseworkService.ListQuery;
import com.cidas.edudoc.coursework.application.StudentCourseworkService.StudentAssignmentView;
import com.cidas.edudoc.coursework.application.StudentCourseworkService.SubmissionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 学生侧作业端点（06 切片 1，Task 1-4 / 1-5）：我的作业列表与提交。 */
@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
public class StudentCourseworkController {

    private final StudentCourseworkService studentCourseworkService;

    @GetMapping("/assignments")
    @RequirePermission(PermissionCode.SUBMISSION_SELF_READ)
    public ApiResponse<PageResult<StudentAssignmentView>> listAssignments(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "courseId", required = false) String courseId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ApiResponse.ok(studentCourseworkService.listAssignments(
                new ListQuery(status, courseId, page, pageSize)));
    }

    /** 提交作业：版本由服务端冻结文档当前版本；重复提交按 attempt_no 递增（原型允许重交）。 */
    @PostMapping("/assignments/{assignmentId}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.DOCUMENT_CREATE)
    public ApiResponse<SubmissionResult> submit(@PathVariable("assignmentId") String assignmentId,
                                                @RequestBody SubmitRequest request) {
        return ApiResponse.ok(studentCourseworkService.submit(assignmentId, request.documentId()));
    }

    public record SubmitRequest(String documentId) {
    }
}
