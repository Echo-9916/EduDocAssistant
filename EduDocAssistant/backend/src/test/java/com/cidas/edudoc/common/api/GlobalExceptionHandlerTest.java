package com.cidas.edudoc.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionMapsCodeAndStatus() {
        CidasBusinessException e = new CidasBusinessException(ErrorCode.FORBIDDEN, "无权限");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleBusiness(e);
        assertEquals(403, resp.getStatusCode().value());
        assertEquals(ErrorCode.FORBIDDEN.code(), resp.getBody().code());
        assertEquals("无权限", resp.getBody().message());
    }

    @Test
    void unknownExceptionMapsInternalError() {
        ResponseEntity<ApiResponse<Void>> resp = handler.handleUnknown(new RuntimeException("boom"));
        assertEquals(500, resp.getStatusCode().value());
        assertEquals(ErrorCode.INTERNAL_ERROR.code(), resp.getBody().code());
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
