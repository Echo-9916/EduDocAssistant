package com.cidas.edudoc.access.api;

import com.cidas.edudoc.access.application.AuditQueryService;
import com.cidas.edudoc.access.application.AuditQueryService.AuditEventDetailView;
import com.cidas.edudoc.access.application.AuditQueryService.AuditEventView;
import com.cidas.edudoc.access.application.AuditQueryService.ListQuery;
import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 运维端审计事件查询（06 切片 1 · REQ-09）：只读，权限点 AUDIT_READ。 */
@RestController
@RequestMapping("/api/v1/ops/audit-events")
@RequiredArgsConstructor
public class AuditQueryController {

    private final AuditQueryService auditQueryService;

    @GetMapping
    @RequirePermission(PermissionCode.AUDIT_READ)
    public ApiResponse<PageResult<AuditEventView>> list(
            @RequestParam(name = "range", required = false) String range,
            @RequestParam(name = "resourceType", required = false) String resourceType,
            @RequestParam(name = "actor", required = false) String actor,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ApiResponse.ok(auditQueryService.search(
                new ListQuery(range, resourceType, actor, keyword, page, pageSize)));
    }

    @GetMapping("/{eventId}")
    @RequirePermission(PermissionCode.AUDIT_READ)
    public ApiResponse<AuditEventDetailView> detail(@PathVariable("eventId") String eventId) {
        return ApiResponse.ok(auditQueryService.detail(eventId));
    }
}
