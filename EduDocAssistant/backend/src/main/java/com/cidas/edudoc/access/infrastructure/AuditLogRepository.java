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

    /** 认证动作写入：登录时 AuthContext 尚未建立，actor 与角色由调用方显式传入。 */
    public void recordAuth(String username, String actorIdHex, String actorRole, String resultCode,
                           String sourceIp, String userAgent) {
        record("AUTH", "USER", username, actorIdHex, actorRole, null, resultCode, null,
                sourceIp, userAgent);
    }

    /** 通用写入：documentIdHex/detailJson 可空，trace 自动关联；sourceIp/userAgent 由请求上下文提供。 */
    public void record(String actionType, String resourceType, String resourceRef, String actorIdHex,
                       String actorRole, String documentIdHex, String resultCode, String detailJson,
                       String sourceIp, String userAgent) {
        mapper.insert(new AuditLog(actorIdHex, actorRole, actionType, resourceType, resourceRef,
                documentIdHex, resultCode, traceId(), detailJson, sourceIp, userAgent));
    }

    private String traceId() {
        return Optional.ofNullable(TraceContext.get()).orElseGet(() -> UUID.randomUUID().toString());
    }

    // ---------- 只读查询（06 切片 1 审计检索）：委托 Mapper，应用层不直连 Mapper ----------

    public long countEvents(java.time.OffsetDateTime since, String resourceType, String actor, String keyword) {
        return mapper.countEvents(since, resourceType, actor, keyword);
    }

    public java.util.List<java.util.Map<String, Object>> searchEvents(java.time.OffsetDateTime since,
                                                                      String resourceType, String actor,
                                                                      String keyword, long offset, int pageSize) {
        return mapper.searchEvents(since, resourceType, actor, keyword, offset, pageSize);
    }

    public java.util.Map<String, Object> selectEventDetail(String eventId) {
        return mapper.selectEventDetail(eventId);
    }

    public java.util.List<java.util.Map<String, Object>> selectChainByTraceId(String traceId) {
        return mapper.selectChainByTraceId(traceId);
    }
}
