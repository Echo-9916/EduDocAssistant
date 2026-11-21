package com.cidas.edudoc.document.domain;

/**
 * 提取出的段落块：文本、所属章节路径与页码（无页信息时页码为 0）。
 * kind 只描述阅读结构（PARAGRAPH/HEADING/LIST_ITEM/TABLE_CELL），不改变文本流与偏移。
 */
public record TextBlock(String text, String headingPath, int pageNo, String kind, Integer level,
                        Integer ordinal, Integer tableNo, Integer row, Integer col, String lang) {

    public static final String PARAGRAPH = "PARAGRAPH";
    public static final String HEADING = "HEADING";
    public static final String LIST_ITEM = "LIST_ITEM";
    public static final String TABLE_CELL = "TABLE_CELL";
    public static final String CODE = "CODE";

    /** 兼容旧调用：无结构信息即普通段落。 */
    public TextBlock(String text, String headingPath, int pageNo) {
        this(text, headingPath, pageNo, PARAGRAPH, null, null, null, null, null, null);
    }

    /** 兼容旧调用：无代码语言。 */
    public TextBlock(String text, String headingPath, int pageNo, String kind, Integer level,
                     Integer ordinal, Integer tableNo, Integer row, Integer col) {
        this(text, headingPath, pageNo, kind, level, ordinal, tableNo, row, col, null);
    }
}