package com.cidas.edudoc.common.api;

import java.time.Instant;

/** 统一响应封装：所有 REST 接口返回该结构，与概要设计 §5.3 envelope 对齐。 */
public record ApiResponse<T>(int code, String message, T data, String traceId, String timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.code(), ErrorCode.OK.defaultMessage(), data,
                TraceContext.get(), Instant.now().toString());
    }

    /** 成功但无数据、仅带提示文案的响应（如占位接口）。 */
    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(ErrorCode.OK.code(), message, null,
                TraceContext.get(), Instant.now().toString());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.defaultMessage());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null,
                TraceContext.get(), Instant.now().toString());
    }
}
