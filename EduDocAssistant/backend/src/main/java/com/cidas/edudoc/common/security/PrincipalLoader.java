package com.cidas.edudoc.common.security;

/** 主体加载端口：由 access 域实现，横切层按 userId 实时装配角色/权限，避免 common 反向依赖业务模块。 */
public interface PrincipalLoader {

    UserPrincipal loadPrincipal(String userIdHex, String username);
}
