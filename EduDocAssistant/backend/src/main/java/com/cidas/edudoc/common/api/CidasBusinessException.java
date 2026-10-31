package com.cidas.edudoc.common.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 业务异常：携带错误码、可重试标记与上下文键，由全局异常处理器映射为标准响应。 */
public class CidasBusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final boolean retryable;
    private final Map<String, Object> context;

    public CidasBusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), false, Map.of());
    }

    public CidasBusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, false, Map.of());
    }

    public CidasBusinessException(ErrorCode errorCode, String message, boolean retryable,
                                  Map<String, Object> context) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }

    public Map<String, Object> context() {
        return context;
    }
}
