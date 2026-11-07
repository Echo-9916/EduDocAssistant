package com.cidas.edudoc.template.domain;

import java.util.List;

/**
 * docx 样式事实快照（D-04）：规则比对的输入。selector 取规则 target_selector 同一词表
 * （HEADING_1..3 / BODY / FIGURE_CAPTION / TABLE_CAPTION / CODE_BLOCK / OTHER）。
 * 1107 第五轮 P2-5：段落显式值下沉 run 级（RunFact），段落级字段为「继承层」（样式链+docDefaults），
 * family()/familyEastAsia()/pt() 兼容读取「首生效值」；混合格式按 run 逐一比对不再被首值掩盖。
 * P3-1：run 携带字符区间（段落文本内 [charStart,charEnd)）。
 */
public record StyleSnapshot(List<ParagraphFact> paragraphs, PageFacts page) {

    /** kind=AUTO（value=倍数）| EXACT/AT_LEAST（value=磅值）。 */
    public record LineSpacing(String kind, Double value) {
        public boolean fixed() {
            return "EXACT".equals(kind) || "AT_LEAST".equals(kind);
        }
    }

    /** run 显式属性（null=继承段落层），text 用于空 run 过滤，charStart/End=段落文本内区间。 */
    public record RunFact(int charStart, int charEnd, String text,
                          String family, String eastAsia, Double pt) {
    }

    public record ParagraphFact(int index, String selector, String inheritedFamily,
                                String inheritedEastAsia, Double inheritedPt,
                                LineSpacing line, String text, List<RunFact> runs) {

        /** 无 run 明细的构造（单格式段落/测试夹具）：段落层值即生效值。 */
        public ParagraphFact(int index, String selector, String family, String familyEastAsia,
                             Double pt, LineSpacing line, String text) {
            this(index, selector, family, familyEastAsia, pt, line, text, List.of());
        }

        public List<RunFact> effectiveRuns() {
            if (runs == null || runs.isEmpty()) {
                return List.of(new RunFact(0, text == null ? 0 : text.length(), text, null, null, null));
            }
            return runs;
        }

        public String family() {
            for (RunFact run : effectiveRuns()) {
                if (run.family() != null) {
                    return run.family();
                }
            }
            return inheritedFamily;
        }

        public String familyEastAsia() {
            for (RunFact run : effectiveRuns()) {
                if (run.eastAsia() != null) {
                    return run.eastAsia();
                }
            }
            return inheritedEastAsia;
        }

        public Double pt() {
            for (RunFact run : effectiveRuns()) {
                if (run.pt() != null) {
                    return run.pt();
                }
            }
            return inheritedPt;
        }

        /** run 生效字体槽值：显式 ?: 继承层。 */
        public String familyOf(RunFact run) {
            return run.family() != null ? run.family() : inheritedFamily;
        }

        public String eastAsiaOf(RunFact run) {
            return run.eastAsia() != null ? run.eastAsia() : inheritedEastAsia;
        }

        public Double ptOf(RunFact run) {
            return run.pt() != null ? run.pt() : inheritedPt;
        }

        public static boolean blank(RunFact run) {
            return run.text() == null || run.text().isBlank();
        }
    }

    /** 纸型宽高（1106 P2-2）与页边距同源 sectPr。 */
    public record PageFacts(Double top, Double bottom, Double left, Double right,
                            Double widthMm, Double heightMm,
                            boolean headerPresent, boolean footerPresent) {
    }

    public List<ParagraphFact> withSelector(String selector) {
        return paragraphs.stream().filter(p -> selector.equals(p.selector())).toList();
    }
}
