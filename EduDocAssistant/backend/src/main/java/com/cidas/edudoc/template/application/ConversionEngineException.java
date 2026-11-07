package com.cidas.edudoc.template.application;

/** 转换引擎结构化失败（code=ENCRYPTED|CORRUPTED|UNSUPPORTED|TIMEOUT|ENGINE_UNAVAILABLE）。 */
public class ConversionEngineException extends RuntimeException {

    private final String code;

    public ConversionEngineException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
