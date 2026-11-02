package com.cidas.edudoc.access.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.access.infrastructure.AuditLogRepository;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void record(String actionType, String resourceType, String resourceRef,
                       String documentIdHex, String resultCode, Map<String, Object> detail) {
        UserPrincipal principal = AuthContext.get();
        auditLogRepository.record(actionType, resourceType, resourceRef,
                principal == null ? null : principal.userId(),
                principal == null || principal.roles().isEmpty() ? null : principal.roles().iterator().next(),
                documentIdHex, resultCode, toJson(detail));
    }

    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
