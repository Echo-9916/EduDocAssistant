package com.cidas.edudoc.access.application;

import java.util.Map;

/** 审计写入端口（详细设计表 3-1）：业务域经本接口记录，不直接触碰 access 仓储。 */
public interface AuditService {

    void record(String actionType, String resourceType, String resourceRef,
                String documentIdHex, String resultCode, Map<String, Object> detail);
}
