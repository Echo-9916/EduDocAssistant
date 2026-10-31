package com.cidas.edudoc.common.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiResponseTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void okSetsCodeDataAndTrace() {
        TraceContext.set("trace-123");

        ApiResponse<String> resp = ApiResponse.ok("payload");

        assertEquals(ErrorCode.OK.code(), resp.code());
        assertEquals("payload", resp.data());
        assertEquals("trace-123", resp.traceId());
        assertNotNull(resp.timestamp());
    }

    @Test
    void errorUsesDefaultMessageAndNullData() {
        ApiResponse<Void> resp = ApiResponse.error(ErrorCode.FORBIDDEN);

        assertEquals(ErrorCode.FORBIDDEN.code(), resp.code());
        assertEquals(ErrorCode.FORBIDDEN.defaultMessage(), resp.message());
        assertNull(resp.data());
    }

    @Test
    void errorOverridesMessage() {
        ApiResponse<Void> resp = ApiResponse.error(ErrorCode.BAD_REQUEST, "字段 x 非法");

        assertEquals("字段 x 非法", resp.message());
    }
}
