package com.cidas.edudoc.template.domain;

/** 模板 docx 解析失败（非 zip/坏样式树），版本回写 INVALID 供人工编辑规则补救。 */
public class TemplateParseException extends RuntimeException {

    public TemplateParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public TemplateParseException(String message) {
        super(message);
    }
}
