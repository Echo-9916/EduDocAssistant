package com.cidas.edudoc.access.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 用户账号持久化对象（edudoc_user 表，id 为 RAW(16) 转 hex 字符串）。 */
@Data
@AllArgsConstructor
public class UserAccount {

    private String id;
    private String username;
    private String displayName;
    private String passwordHash;
    private String accountStatus;
    private String authSource;
}
