package com.cidas.edudoc.coursework.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.coursework.application.StudentWorkbenchService;
import com.cidas.edudoc.coursework.application.StudentWorkbenchService.View;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 学生工作台聚合端点：一次取回统计卡、待办与最近文档，前端按需渲染。 */
@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
public class StudentWorkbenchController {

    private final StudentWorkbenchService studentWorkbenchService;

    @GetMapping("/workbench-summary")
    @RequirePermission(PermissionCode.SUBMISSION_SELF_READ)
    public ApiResponse<View> summary() {
        return ApiResponse.ok(studentWorkbenchService.summary());
    }
}