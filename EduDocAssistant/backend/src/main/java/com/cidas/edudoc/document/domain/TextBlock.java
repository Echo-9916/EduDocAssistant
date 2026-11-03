package com.cidas.edudoc.document.domain;

/** 提取出的段落块：文本、所属章节路径与页码（无页信息时页码为 0）。 */
public record TextBlock(String text, String headingPath, int pageNo) {
}
