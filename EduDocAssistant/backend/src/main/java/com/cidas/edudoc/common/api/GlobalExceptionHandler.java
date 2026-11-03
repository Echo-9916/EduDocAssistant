package com.cidas.edudoc.common.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * 全局异常处理器：业务异常映射为标准响应；框架异常按 HTTP↔业务 code 一一对应回落（1103 审查 S1），
 * 客户端错误一律 WARN 级、不泄露堆栈；其余未知异常统一转 50001。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CidasBusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(CidasBusinessException e) {
        log.warn("业务异常 code={} retryable={} traceId={}", e.errorCode().code(), e.retryable(),
                TraceContext.get(), e);
        // context 非空时随 data 下发（如 accountStatus），前端按结构化字段分支（P2-2）
        Object data = e.context().isEmpty() ? null : e.context();
        return ResponseEntity.status(e.errorCode().httpStatus())
                .body(ApiResponse.error(e.errorCode(), e.getMessage(), data));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoRoute(NoResourceFoundException e) {
        return clientError(ErrorCode.NOT_FOUND, "资源不存在或不可见", e);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethod(HttpRequestMethodNotSupportedException e) {
        return clientError(ErrorCode.METHOD_NOT_ALLOWED, "请求方法不允许", e);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return clientError(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Content-Type 不支持", e);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            BindException.class})
    public ResponseEntity<ApiResponse<Object>> handleMalformedRequest(Exception e) {
        return clientError(ErrorCode.BAD_REQUEST, "请求参数不合法", e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnknown(Exception e) {
        log.error("未捕获异常 traceId={}", TraceContext.get(), e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }

    /** 客户端错误：WARN 级、只回固定文案，原始异常细节仅进日志（S6 同源原则）。 */
    private ResponseEntity<ApiResponse<Object>> clientError(ErrorCode code, String message, Exception e) {
        log.warn("客户端请求异常 code={} traceId={} 原因={}", code.code(), TraceContext.get(), e.getMessage());
        return ResponseEntity.status(code.httpStatus()).body(ApiResponse.error(code, message));
    }
}
