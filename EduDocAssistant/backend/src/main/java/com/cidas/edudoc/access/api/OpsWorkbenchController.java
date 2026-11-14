package com.cidas.edudoc.access.api;

import com.cidas.edudoc.access.application.OpsWorkbenchService;
import com.cidas.edudoc.access.application.OpsWorkbenchService.Summary;
import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维工作台计数摘要。路径已被 SecurityConfig 的 {@code /api/v1/ops/** hasRole("OPS")} 兜底，
 * 再叠加既有 AUDIT_READ 权限点（沿用 06 切片 1 的写法，不新增权限点）。
 */
@RestController
@RequestMapping("/api/v1/ops/workbench-summary")
@RequiredArgsConstructor
public class OpsWorkbenchController {

    private final OpsWorkbenchService opsWorkbenchService;

    @GetMapping
    @RequirePermission(PermissionCode.AUDIT_READ)
    public ApiResponse<Summary> summary() {
        return ApiResponse.ok(opsWorkbenchService.summary());
    }
}