package com.cidas.edudoc.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionMapsCodeAndStatus() {
        CidasBusinessException e = new CidasBusinessException(ErrorCode.FORBIDDEN, "无权限");
        ResponseEntity<ApiResponse<Object>> resp = handler.handleBusiness(e);
        assertEquals(403, resp.getStatusCode().value());
        assertEquals(ErrorCode.FORBIDDEN.code(), resp.getBody().code());
        assertEquals("无权限", resp.getBody().message());
        assertNull(resp.getBody().data());
    }

    @Test
    void businessExceptionContextExposedAsData() {
        CidasBusinessException e = new CidasBusinessException(ErrorCode.UNAUTHORIZED, "账号受限：账号已锁定",
                false, Map.of("accountStatus", "L"));
        ResponseEntity<ApiResponse<Object>> resp = handler.handleBusiness(e);
        assertEquals(Map.of("accountStatus", "L"), resp.getBody().data());
    }

    @Test
    void unknownExceptionMapsInternalError() {
        ResponseEntity<ApiResponse<Object>> resp = handler.handleUnknown(new RuntimeException("boom"));
        assertEquals(500, resp.getStatusCode().value());
        assertEquals(ErrorCode.INTERNAL_ERROR.code(), resp.getBody().code());
    }

    @Test
    void frameworkExceptionsMapToClientErrors() {
        // 1103 审查 S1：8 类框架输入不得再塌缩为 500/50001
        var noRoute = handler.handleNoRoute(
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "/api/v1/definitely-not-a-route"));
        assertEquals(404, noRoute.getStatusCode().value());
        assertEquals(40401, noRoute.getBody().code());

        var method = handler.handleMethod(
                new org.springframework.web.HttpRequestMethodNotSupportedException("DELETE"));
        assertEquals(405, method.getStatusCode().value());
        assertEquals(40501, method.getBody().code());

        var media = handler.handleMediaType(
                new org.springframework.web.HttpMediaTypeNotSupportedException("text/plain"));
        assertEquals(415, media.getStatusCode().value());
        assertEquals(41501, media.getBody().code());

        var body = handler.handleMalformedRequest(
                new org.springframework.http.converter.HttpMessageNotReadableException(
                        "bad json", mock(org.springframework.http.HttpInputMessage.class)));
        assertEquals(400, body.getStatusCode().value());
        assertEquals(40001, body.getBody().code());
    }

    @Test
    void errorCodesCoverDesignRange() {
        for (ErrorCode c : ErrorCode.values()) {
            if (c == ErrorCode.OK) {
                continue;
            }
            assertTrue(c.code() >= 40001 && c.code() <= 50401, "错误码超出设计范围: " + c.code());
        }
    }
}
