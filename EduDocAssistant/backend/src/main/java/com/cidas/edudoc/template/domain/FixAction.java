package com.cidas.edudoc.template.domain;

import java.util.Map;

/**
 * 样式修复动作（R1 修复面 + 纸型，第四轮复审 P1-1/Standards 收口）：
 * 由单 record 多可空字段改为按动作类型区分的密封变体——杜绝「新增纸型字段遗漏动作分支」类 data clump。
 */
public sealed interface FixAction {

    /** 段落字体：family=ASCII 西文槽、eastAsia=中文槽，null 侧不动（P1-1 双槽独立写回）。 */
    record Font(int paragraphIndex, String family, String eastAsia) implements FixAction {
    }

    record Size(int paragraphIndex, double pt) implements FixAction {
    }

    /** kind=AUTO 时 value=倍数；EXACT/AT_LEAST 时 value=磅值（第四轮 P1-2：固定行距可修复）。 */
    record Line(int paragraphIndex, String kind, double value) implements FixAction {
    }

    /** 页面级：top/bottom/left/right（mm）+ width/height（纸型 mm，P1-1 贯通 pgSz）。 */
    record Page(Map<String, Double> marginsMm) implements FixAction {
    }
}
