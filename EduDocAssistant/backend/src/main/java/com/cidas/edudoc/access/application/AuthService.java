package com.cidas.edudoc.access.application;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.JwtService;
import com.cidas.edudoc.common.security.UserPrincipal;
import org.springframework.stereotype.Service;

/** 认证编排：登录签发令牌、查询当前主体、登出。 */
@Service
public class AuthService {

    private final AuthProvider authProvider;
    private final JwtService jwtService;

    public AuthService(AuthProvider authProvider, JwtService jwtService) {
        this.authProvider = authProvider;
        this.jwtService = jwtService;
    }

    public LoginResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "用户名或密码不能为空");
        }
        UserPrincipal principal = authProvider.authenticate(username.trim(), password)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));
        String token = jwtService.issue(principal.userId(), principal.username());
        return new LoginResult(token, principal);
    }

    public UserPrincipal me() {
        return AuthContext.require();
    }

    public void logout() {
        // ponytail: 无状态 JWT 无法服务端撤销，登出仅丢弃客户端令牌；
        // 需要 40102 主动撤销时再加 token 版本号或黑名单表。
    }

    public record LoginResult(String token, UserPrincipal user) {
    }
}
