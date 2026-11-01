package com.cidas.edudoc.access.application.impl;

import com.cidas.edudoc.access.application.AuthProvider;
import com.cidas.edudoc.access.application.AuthService;
import com.cidas.edudoc.access.infrastructure.AuditLogRepository;
import com.cidas.edudoc.access.infrastructure.UserAccountRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.JwtService;
import com.cidas.edudoc.common.security.TokenRevocationStore;
import com.cidas.edudoc.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 认证编排实现：登录签发令牌、查询当前主体、登出。 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthProvider authProvider;
    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;
    private final AuditLogRepository auditLogRepository;
    private final TokenRevocationStore tokenRevocationStore;

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
            auditLogRepository.recordAuth(trimmed, null, String.valueOf(e.errorCode().code()));
            throw e;
        }
        userAccountRepository.updateLastLoginTime(principal.userId());
        auditLogRepository.recordAuth(trimmed, principal.userId(), "SUCCESS");
        String token = jwtService.issue(principal.userId(), principal.username(), principal.roles());
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
