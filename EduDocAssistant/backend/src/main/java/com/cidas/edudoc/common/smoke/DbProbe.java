package com.cidas.edudoc.common.smoke;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 数据库探针：验证 Oracle 连接并读取种子角色数。 */
@Component
public class DbProbe {

    private final JdbcTemplate jdbcTemplate;

    public DbProbe(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SmokeResult.DbStatus probe() {
        Integer roleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edudoc_role", Integer.class);
        return new SmokeResult.DbStatus("ok", roleCount == null ? -1 : roleCount);
    }
}
