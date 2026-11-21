package com.cidas.edudoc.document.domain;

/**
 * 阅读结构节点，与 chunk 使用同一坐标系（全文 UTF-16 code unit、闭区间 [start, end]）。
 *
 * <p>两类节点：
 * <ul>
 *   <li>区间节点：文字本来就在正文流里（p / 列表项 / 表格单元格 / 中文样式标题），start/end 非空；</li>
 *   <li>标记节点：文字不在正文流里（Tika 的 h1..h6 只更新标题栈），text + anchor 非空、不占字符。</li>
 * </ul>
 * 只存区间与元数据，文本由前端从 blocks 拼出的全文切片，保持单一真源。
 */
public record StructureNode(int no, String kind, Integer level, String text, Integer anchor,
                            Integer start, Integer end, String headingPath, Integer pageNo,
                            Integer ordinal, Integer tableNo, Integer row, Integer col, String lang) {

    public static StructureNode ranged(int no, String kind, int start, int end, String headingPath,
                                       Integer pageNo, Integer level, Integer ordinal,
                                       Integer tableNo, Integer row, Integer col) {
        return ranged(no, kind, start, end, headingPath, pageNo, level, ordinal, tableNo, row, col, null);
    }

    public static StructureNode ranged(int no, String kind, int start, int end, String headingPath,
                                       Integer pageNo, Integer level, Integer ordinal,
                                       Integer tableNo, Integer row, Integer col, String lang) {
        return new StructureNode(no, kind, level, null, null, start, end, headingPath, pageNo,
                ordinal, tableNo, row, col, lang);
    }

    public static StructureNode marker(int no, int level, String text, int anchor) {
        return new StructureNode(no, TextBlock.HEADING, level, text, anchor, null, null, null, null,
                null, null, null, null, null);
    }
}