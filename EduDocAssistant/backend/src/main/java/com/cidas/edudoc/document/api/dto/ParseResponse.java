package com.cidas.edudoc.document.api.dto;

/** 重新解析响应：taskId = outbox eventId hex。 */
public record ParseResponse(String taskId) {
}
