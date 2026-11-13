package com.cidas.edudoc.access.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.access.infrastructure.AuditLogRepository;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.RequestMeta;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void record(String actionType, String resourceType, String resourceRef,
                       String documentIdHex, String resultCode, Map<String, Object> detail) {
        UserPrincipal principal = AuthContext.get();
        recordInternal(actionType, resourceType, resourceRef,
                principal == null ? null : principal.userId(),
                principal == null || principal.roles().isEmpty() ? null : principal.roles().iterator().next(),
                documentIdHex, resultCode, detail);
    }

    @Override
    public void recordAuth(String username, String actorIdHex, String actorRole, String resultCode) {
        recordInternal("AUTH", "USER", username, actorIdHex, actorRole, null, resultCode, null);
    }

    /** 请求元数据统一在这里取：调用方（含异步 Worker）无需关心 Servlet 上下文是否存在。 */
    private void recordInternal(String actionType, String resourceType, String resourceRef,
                                String actorIdHex, String actorRole, String documentIdHex,
                                String resultCode, Map<String, Object> detail) {
        auditLogRepository.record(actionType, resourceType, resourceRef, actorIdHex, actorRole,
                documentIdHex, resultCode, toJson(detail), RequestMeta.clientIp(), RequestMeta.userAgent());
    }

    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            // 1103 审查 S7：审计明细是"写不进去就要吵"的数据，静默丢之前至少留痕
            log.warn("审计明细序列化失败，detail_json 将为空 action 上下文={}", detail.keySet(), e);
            return null;
        }
    }
}
