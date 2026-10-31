package com.cidas.edudoc.common.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 全局异常处理器：业务异常映射为标准响应，未知异常统一转 50001。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CidasBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(CidasBusinessException e) {
        log.warn("业务异常 code={} retryable={} traceId={}", e.errorCode().code(), e.retryable(),
                TraceContext.get(), e);
        return ResponseEntity.status(e.errorCode().httpStatus())
                .body(ApiResponse.error(e.errorCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("未捕获异常 traceId={}", TraceContext.get(), e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }
}
