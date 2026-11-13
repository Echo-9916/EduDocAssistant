package com.cidas.edudoc.access.application;

import java.util.Map;

/** 审计写入端口（详细设计表 3-1）：业务域经本接口记录，不直接触碰 access 仓储。 */
public interface AuditService {

    void record(String actionType, String resourceType, String resourceRef,
                String documentIdHex, String resultCode, Map<String, Object> detail);

    /**
     * 认证链路专用：登录时 AuthContext 尚未建立，操作者与角色需显式传入；
     * 来源 IP / 终端由实现层从当前 HttpServletRequest 补全。
     */
    void recordAuth(String username, String actorIdHex, String actorRole, String resultCode);
}
