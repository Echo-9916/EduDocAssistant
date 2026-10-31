package com.cidas.edudoc.access.application;

import com.cidas.edudoc.common.security.UserPrincipal;

import java.util.Optional;

/** 认证适配层：LOCAL 与 SSO 各自实现，认证成功返回主体，凭据错误返回空。 */
public interface AuthProvider {

    Optional<UserPrincipal> authenticate(String username, String password);
}
