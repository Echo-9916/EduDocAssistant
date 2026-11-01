package com.cidas.edudoc.access.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 审计日志持久化对象（edudoc_audit_log 表）。 */
@Data
@AllArgsConstructor
public class AuditLog {

    private String actorIdHex;
    private String actorRole;
    private String actionType;
    private String resourceType;
    private String resourceRef;
    private String resultCode;
    private String traceId;
}
