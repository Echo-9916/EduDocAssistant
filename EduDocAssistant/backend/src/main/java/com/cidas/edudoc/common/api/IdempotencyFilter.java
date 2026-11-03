package com.cidas.edudoc.common.api;

import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 写请求（POST/PUT/PATCH/DELETE）校验 Idempotency-Key：缺失 40001，重复 40902。认证接口无需幂等。
 * 键作用域 = 认证主体 + 方法 + 路径（1103 审查 S4，Stripe/IETF 惯例）：跨用户、跨端点互不顶替。
 */
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isWrite(request.getMethod()) && requiresIdempotency(request.getRequestURI())) {
            String key = request.getHeader(HEADER);
            if (key == null || key.isBlank()) {
                writeError(response, ErrorCode.BAD_REQUEST, "写请求缺少 " + HEADER);
                return;
            }
            if (!idempotencyStore.tryAcquire(scopedKey(request, key))) {
                writeError(response, ErrorCode.DUPLICATE, ErrorCode.DUPLICATE.defaultMessage());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String scopedKey(HttpServletRequest request, String key) {
        UserPrincipal principal = AuthContext.get();
        String userId = principal == null ? "-" : principal.userId();
        return userId + ":" + request.getMethod() + ":" + request.getRequestURI() + ":" + key;
    }

    private static boolean isWrite(String method) {
        return WRITE_METHODS.contains(method.toUpperCase());
    }

    private static boolean requiresIdempotency(String uri) {
        return uri.startsWith("/api/") && !uri.startsWith("/api/v1/auth/");
    }

    private void writeError(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code, message));
    }
}
