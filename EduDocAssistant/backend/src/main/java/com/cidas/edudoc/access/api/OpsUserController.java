package com.cidas.edudoc.access.api;

import com.cidas.edudoc.access.application.OpsUserService;
import com.cidas.edudoc.access.application.OpsUserService.Query;
import com.cidas.edudoc.access.application.OpsUserService.UserView;
import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维用户目录与冻结/解冻（REQ 运维 07/08）。
 * 路径已被 SecurityConfig 的 {@code /api/v1/ops/** hasRole("OPS")} 再兜一层。
 */
@RestController
@RequestMapping("/api/v1/ops/users")
@RequiredArgsConstructor
public class OpsUserController {

    private final OpsUserService opsUserService;

    @GetMapping
    @RequirePermission(PermissionCode.ROLE_MANAGE)
    public ApiResponse<PageResult<UserView>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ApiResponse.ok(opsUserService.list(new Query(keyword, role, status, page, pageSize)));
    }

    @PostMapping("/{userId}/freeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(PermissionCode.ROLE_MANAGE)
    public void freeze(@PathVariable("userId") String userId) {
        opsUserService.freeze(userId);
    }

    @PostMapping("/{userId}/unfreeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(PermissionCode.ROLE_MANAGE)
    public void unfreeze(@PathVariable("userId") String userId) {
        opsUserService.unfreeze(userId);
    }
}