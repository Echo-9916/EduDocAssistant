package com.cidas.edudoc.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * 当前请求的来源 IP 与客户端标识，供审计落库使用。
 *
 * <p>取不到请求（异步 Worker、启动期任务）时返回 null：系统动作不应伪造终端信息。
 * 列宽口径：source_ip 64、user_agent 500（见 01_schema.sql），超长直接截断。
 */
public final class RequestMeta {

    private static final int IP_MAX = 64;
    private static final int UA_MAX = 500;

    private RequestMeta() {
    }

    public static String clientIp() {
        HttpServletRequest request = current();
        if (request == null) {
            return null;
        }
        // Nginx 反代后 remoteAddr 是网关地址：优先 X-Forwarded-For 首个非空段，其次 X-Real-IP
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return truncate(first, IP_MAX);
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncate(realIp.trim(), IP_MAX);
        }
        return truncate(request.getRemoteAddr(), IP_MAX);
    }

    public static String userAgent() {
        HttpServletRequest request = current();
        return request == null ? null : truncate(request.getHeader("User-Agent"), UA_MAX);
    }

    private static HttpServletRequest current() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .orElse(null);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}