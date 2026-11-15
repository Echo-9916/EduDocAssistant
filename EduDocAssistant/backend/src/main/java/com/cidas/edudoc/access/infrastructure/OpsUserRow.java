package com.cidas.edudoc.access.infrastructure;

import lombok.Data;

/** 运维用户目录行（用户 + 角色 + 最近登录投影）。 */
@Data
public class OpsUserRow {

    private String id;
    private String username;
    private String displayName;
    private String role;
    private String accountStatus;
    private String department;
    private String lastLoginTime;
}