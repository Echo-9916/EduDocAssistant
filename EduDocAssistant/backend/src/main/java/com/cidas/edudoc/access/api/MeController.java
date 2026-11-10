package com.cidas.edudoc.access.api;

import com.cidas.edudoc.access.application.MeService;
import com.cidas.edudoc.access.application.MeService.LoginRecordView;
import com.cidas.edudoc.access.application.MeService.PasswordChange;
import com.cidas.edudoc.access.application.MeService.ProfileView;
import com.cidas.edudoc.access.application.MeService.SecurityUpdate;
import com.cidas.edudoc.access.application.MeService.SecurityView;
import com.cidas.edudoc.access.application.MeService.SessionView;
import com.cidas.edudoc.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 个人中心端点（06 切片 2 · 任务 2-6）：全部「本人」权限——认证即可调用，
 * 身份取 AuthContext，不挂 @RequirePermission；写端点幂等由全局 IdempotencyFilter 强制。 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;

    @GetMapping("/profile")
    public ApiResponse<ProfileView> profile() {
        return ApiResponse.ok(meService.profile());
    }

    /** 用 Map 收 body 以便拒绝白名单外的键（学号/角色等同步字段出现即 40001）。 */
    @PutMapping("/profile")
    public ApiResponse<ProfileView> updateProfile(@RequestBody Map<String, Object> patch) {
        return ApiResponse.ok(meService.updateProfile(patch));
    }

    @GetMapping("/security")
    public ApiResponse<SecurityView> security() {
        return ApiResponse.ok(meService.security());
    }

    @PutMapping("/security")
    public ApiResponse<SecurityView> updateSecurity(@RequestBody SecurityUpdate update) {
        return ApiResponse.ok(meService.updateSecurity(update));
    }

    /** 改密成功后当前令牌即被撤销（强制重登）；他处令牌在 TTL 内自然过期（见 MeServiceImpl 取舍注释）。 */
    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody PasswordChangeRequest request,
                                            @RequestHeader("Authorization") String authorization) {
        meService.changePassword(new PasswordChange(request.oldPassword(), request.newPassword(),
                authorization.substring(7)));
        return ApiResponse.message("密码已修改，请使用新密码重新登录；其他设备的登录将在 30 分钟内失效");
    }

    @GetMapping("/sessions")
    public ApiResponse<List<SessionView>> sessions() {
        return ApiResponse.ok(meService.sessions());
    }

    /** 现阶段 endSession 必抛 40901（当前会话不可下线；他处会话缺令牌索引无法撤销），成功分支为将来占位。 */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> endSession(@PathVariable("sessionId") String sessionId) {
        meService.endSession(sessionId);
        return ApiResponse.message("会话已下线");
    }

    @GetMapping("/login-records")
    public ApiResponse<List<LoginRecordView>> loginRecords(
            @RequestParam(name = "range", required = false) String range) {
        return ApiResponse.ok(meService.loginRecords(range));
    }

    public record PasswordChangeRequest(String oldPassword, String newPassword) {
    }
}
