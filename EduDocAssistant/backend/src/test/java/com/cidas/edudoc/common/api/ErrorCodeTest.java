package com.cidas.edudoc.common.api;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorCodeTest {

    @Test
    void codesAreUniqueAndMatchHttpStatusPrefix() {
        Set<Integer> seen = new HashSet<>();
        for (ErrorCode c : ErrorCode.values()) {
            assertTrue(seen.add(c.code()), "错误码重复: " + c.code());
            if (c != ErrorCode.OK) {
                assertEquals(c.code() / 100, c.httpStatus().value(), "HTTP 状态与错误码前缀不一致: " + c);
            }
        }
    }

    @Test
    void countMatchesDesignDictionary() {
        // 1 个成功码 + 18 个错误码（详细设计 §5.1）
        assertEquals(19, ErrorCode.values().length);
    }
}
