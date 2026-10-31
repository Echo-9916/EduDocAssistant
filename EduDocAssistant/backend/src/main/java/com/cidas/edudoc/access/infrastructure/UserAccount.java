package com.cidas.edudoc.access.infrastructure;

/** 用户账号记录（edudoc_user 的登录相关字段）。 */
public record UserAccount(String id, String username, String displayName,
                          String passwordHash, String accountStatus, String authSource) {
}
