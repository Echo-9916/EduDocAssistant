package com.cidas.edudoc.access.infrastructure;

import com.cidas.edudoc.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** 用户账号、角色与权限点的装配查询（SQL 委托给 {@link UserAccountMapper}）。 */
@Repository
@RequiredArgsConstructor
public class UserAccountRepository {

    private static final String STATUS_ACTIVE = "A";

    private final UserAccountMapper mapper;

    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(mapper.selectByUsername(username));
    }

    public UserPrincipal loadPrincipal(String userIdHex, String username) {
        // 鉴权顺序第 2 步：账号状态，非正常（锁定/未激活/注销）直接拒绝
        if (!STATUS_ACTIVE.equals(mapper.selectAccountStatus(userIdHex))) {
            return null;
        }
        Set<String> roles = new HashSet<>(mapper.selectRoleCodes(userIdHex));
        Set<String> permissions = new HashSet<>(mapper.selectPermissionCodes(userIdHex));
        // ponytail: courseScope 待课程成员模块落地后填充
        return new UserPrincipal(userIdHex, username, roles, permissions, Set.of());
    }
}
