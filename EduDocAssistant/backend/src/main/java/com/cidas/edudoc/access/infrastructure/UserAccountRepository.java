package com.cidas.edudoc.access.infrastructure;

import com.cidas.edudoc.common.security.UserPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 用户账号、角色与权限点的查询（RAW(16) 主键以 HEXTORAW/RAWTOHEX 转 hex 字符串）。 */
@Repository
public class UserAccountRepository {

    private final JdbcTemplate jdbc;

    public UserAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserAccount> findByUsername(String username) {
        String sql = """
                SELECT RAWTOHEX(id) AS id, username, display_name, password_hash,
                       account_status, auth_source
                FROM edudoc_user WHERE username = ?
                """;
        return jdbc.query(sql, (rs, i) -> new UserAccount(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                rs.getString("account_status"),
                rs.getString("auth_source")), username).stream().findFirst();
    }

    public UserPrincipal loadPrincipal(String userIdHex, String username) {
        // 鉴权顺序第 2 步：账号状态，非正常（锁定/未激活/注销）直接拒绝
        if (!isActive(userIdHex)) {
            return null;
        }
        Set<String> roles = findRoleCodes(userIdHex);
        Set<String> permissions = findPermissionCodes(userIdHex);
        // ponytail: courseScope 待课程成员模块落地后填充
        return new UserPrincipal(userIdHex, username, roles, permissions, Set.of());
    }

    private boolean isActive(String userIdHex) {
        List<String> statuses = jdbc.queryForList(
                "SELECT account_status FROM edudoc_user WHERE id = HEXTORAW(?)",
                String.class, userIdHex);
        return !statuses.isEmpty() && "A".equals(statuses.get(0));
    }

    private Set<String> findRoleCodes(String userIdHex) {
        String sql = """
                SELECT r.role_code FROM edudoc_role r
                JOIN edudoc_user_role ur ON r.id = ur.role_id
                WHERE ur.user_id = HEXTORAW(?) AND r.status = 'A'
                  AND (ur.effective_to IS NULL OR ur.effective_to > SYSTIMESTAMP)
                """;
        return new HashSet<>(jdbc.queryForList(sql, String.class, userIdHex));
    }

    private Set<String> findPermissionCodes(String userIdHex) {
        String sql = """
                SELECT DISTINCT p.permission_code FROM edudoc_permission p
                JOIN edudoc_role_permission rp ON p.id = rp.permission_id
                JOIN edudoc_user_role ur ON rp.role_id = ur.role_id
                WHERE ur.user_id = HEXTORAW(?)
                  AND (ur.effective_to IS NULL OR ur.effective_to > SYSTIMESTAMP)
                """;
        return new HashSet<>(jdbc.queryForList(sql, String.class, userIdHex));
    }
}
