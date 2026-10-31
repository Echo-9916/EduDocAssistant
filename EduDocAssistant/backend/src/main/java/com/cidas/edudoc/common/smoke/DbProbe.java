package com.cidas.edudoc.common.smoke;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 数据库探针：验证 Oracle 连接并读取种子角色数。 */
@Component
@RequiredArgsConstructor
public class DbProbe {

    private final SmokeMapper smokeMapper;

    public SmokeResult.DbStatus probe() {
        return new SmokeResult.DbStatus("ok", smokeMapper.selectRoleCount());
    }
}
