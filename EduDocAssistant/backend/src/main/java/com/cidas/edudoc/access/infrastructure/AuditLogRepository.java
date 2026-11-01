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
        mapper.insert(new AuditLog(actorIdHex, null, "AUTH", "USER", username, resultCode, traceId()));
    }

    private String traceId() {
        return Optional.ofNullable(TraceContext.get()).orElseGet(() -> UUID.randomUUID().toString());
    }
}
