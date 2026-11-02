package com.cidas.edudoc.document.domain;

/** 文档类型，与 edudoc_document.doc_type CHECK 约束一致。 */
public enum DocumentType {

    COURSEWARE, HOMEWORK, THESIS, REPORT;

    /** 忽略大小写解析，非法返回 null。 */
    public static DocumentType from(String code) {
        if (code == null) {
            return null;
        }
        for (DocumentType type : values()) {
            if (type.name().equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        return null;
    }
}
