package com.cidas.edudoc.access.infrastructure;

import com.cidas.edudoc.common.api.TraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** 审计日志写入（认证动作），不落口令等敏感信息。 */
@Repository
@RequiredArgsConstructor
public class AuditLogRepository {

    private final AuditLogMapper mapper;

    public void recordAuth(String username, String actorIdHex, String resultCode) {
        record("AUTH", "USER", username, actorIdHex, null, null, resultCode, null);
    }

    /** 通用写入：documentIdHex/detailJson 可空，trace 自动关联。 */
    public void record(String actionType, String resourceType, String resourceRef, String actorIdHex,
                       String actorRole, String documentIdHex, String resultCode, String detailJson) {
        mapper.insert(new AuditLog(actorIdHex, actorRole, actionType, resourceType, resourceRef,
                documentIdHex, resultCode, traceId(), detailJson));
    }

    private String traceId() {
        return Optional.ofNullable(TraceContext.get()).orElseGet(() -> UUID.randomUUID().toString());
    }
}
