package com.cidas.edudoc.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 当前登录用户主体：既作安全上下文，也作 /auth/me 响应。 */
public record UserPrincipal(String userId, String username, Set<String> roles,
                            Set<String> permissions, Set<String> courseScope) {

    /** 角色映射为 ROLE_ 前缀，权限点映射为裸编码，供 hasRole/hasAuthority 判定。 */
    public List<GrantedAuthority> authorities() {
        List<GrantedAuthority> list = new ArrayList<>(roles.size() + permissions.size());
        for (String role : roles) {
            list.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        for (String permission : permissions) {
            list.add(new SimpleGrantedAuthority(permission));
        }
        return list;
    }
}
