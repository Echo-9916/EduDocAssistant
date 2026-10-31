package com.cidas.edudoc.common.security;

/** 当前登录用户的线程本地持有，由 {@link JwtAuthenticationFilter} 设置与清理。 */
public final class AuthContext {

    private static final ThreadLocal<UserPrincipal> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(UserPrincipal principal) {
        HOLDER.set(principal);
    }

    public static UserPrincipal get() {
        return HOLDER.get();
    }

    public static UserPrincipal require() {
        UserPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new IllegalStateException("未建立登录上下文");
        }
        return principal;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
