package com.cidas.edudoc.access.application;

import com.cidas.edudoc.common.security.UserPrincipal;

/** 认证编排：登录签发令牌、查询当前主体、登出。 */
public interface AuthService {

    LoginResult login(String username, String password);

    UserPrincipal me();

    void logout(String token);

    record LoginResult(String token, UserPrincipal user) {
    }
}
