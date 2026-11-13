package com.cidas.edudoc.access.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.access.application.AuthProvider;
import com.cidas.edudoc.access.application.AuthService;
import com.cidas.edudoc.access.infrastructure.UserAccountRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.JwtService;
import com.cidas.edudoc.common.security.TokenRevocationStore;
import com.cidas.edudoc.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 认证编排实现：登录签发令牌、查询当前主体、登出。 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthProvider authProvider;
    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;
    private final AuditService auditService;
    private final TokenRevocationStore tokenRevocationStore;
    private final TransactionTemplate transactionTemplate;

    @Override
    public AuthService.LoginResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "用户名或密码不能为空");
        }
        String trimmed = username.trim();
        UserPrincipal principal;
        try {
            principal = authProvider.authenticate(trimmed, password)
                    .orElseThrow(() -> new CidasBusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));
        } catch (CidasBusinessException e) {
            // 拒绝审计独立于成功事务，登录被拒也必须留存（自动提交，不随异常回滚）；
            // 拒绝时无可信角色，actor_role 留空，来源 IP / 终端由 AuditService 从当前请求补全
            auditService.recordAuth(trimmed, null, null, String.valueOf(e.errorCode().code()));
            throw e;
        }
        // P2-1：登录时间 + SUCCESS 审计 + 令牌签发同事务，任一失败整体回滚，不留「成功登录」虚假审计
        String token = transactionTemplate.execute(status -> {
            userAccountRepository.updateLastLoginTime(principal.userId());
            auditService.recordAuth(trimmed, principal.userId(),
                    principal.roles().isEmpty() ? null : principal.roles().iterator().next(), "SUCCESS");
            return jwtService.issue(principal.userId(), principal.username(), principal.roles());
        });
        return new AuthService.LoginResult(token, principal);
    }

    @Override
    public UserPrincipal me() {
        return AuthContext.require();
    }

    @Override
    public void logout(String token) {
        JwtService.TokenClaims claims = jwtService.parse(token);
        tokenRevocationStore.revoke(claims.jti(), claims.expiresAt());
    }
}
