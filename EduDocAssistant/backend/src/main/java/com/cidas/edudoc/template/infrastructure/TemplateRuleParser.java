package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.template.domain.RuleDraft;
import com.cidas.edudoc.template.domain.StyleSnapshot.LineSpacing;
import com.cidas.edudoc.template.domain.TemplateParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDocDefaults;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Word 模板 → 格式规则解析（详细设计 §3.3.1）：读取 styles.xml 的标题/正文样式、sectPr 页边距、
 * 页眉页脚存在性、题注段、代码样式与「参考文献」标题，产出 {@link RuleDraft}（规则 JSON 契约见任务文档 D-02）。
 * ponytail: 仅覆盖字典内七类规则 + HEADER_FOOTER 扩展位（rule_type 无 CHECK 约束）；
 * 缺样式即跳过该规则（模板作者可在规则编辑器补），不做 docDefaults 继承链推导。
 */
@Component
@RequiredArgsConstructor
public class TemplateRuleParser {

    private static final Pattern CAPTION_START = Pattern.compile("^([图表])\\s*(\\d+)(?:([-－.])\\s*\\d+)?");

    private final ObjectMapper mapper;

    public List<RuleDraft> parse(byte[] docxBytes) {
        List<RuleDraft> rules = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            XWPFStyles styles = doc.getStyles();
            if (styles != null) {
                for (int level = 1; level <= 3; level++) {
                    String headingId = findStyleIdByName(styles, "heading " + level);
                    if (headingId != null) {
                        emitStyleRules(rules, "HEADING_" + level,
                                DocxStyleExtractor.styleChain(styles, headingId), defaults(styles), true);
                    }
                }
                String bodyId = findStyleIdByName(styles, "normal");
                if (bodyId != null) {
                    emitStyleRules(rules, "BODY", DocxStyleExtractor.styleChain(styles, bodyId),
                            defaults(styles), false);
                }
                emitCaptionRules(rules, doc, styles);
                emitCodeRule(rules, styles);
            }
            emitReferenceRule(rules, doc);
            emitPageRules(rules, doc);
        } catch (TemplateParseException e) {
            throw e;
        } catch (java.io.IOException | RuntimeException e) {
            throw new TemplateParseException("模板文件无法解析：" + e.getMessage(), e);
        }
        return rules;
    }

    /**
     * 复审 P1-1：模板样式属性沿 basedOn 链解析（Heading 继承 Normal 字体也能产出规则）；
     * FONT 规则同时保存 ascii 与 East Asia 中文槽（两槽独立比对）。
     */
    private void emitStyleRules(List<RuleDraft> out, String selector, List<CTStyle> chain,
                                   CTDocDefaults defaults, boolean heading) {
        String ascii = null;
        String eastAsia = null;
        Double pt = null;
        LineSpacing line = null;
        for (CTStyle style : chain) {
            CTRPr rPr = style.isSetRPr() ? style.getRPr() : null;
            if (ascii == null) {
                ascii = DocxStyleExtractor.rPrFont(rPr, true);
            }
            if (eastAsia == null) {
                eastAsia = DocxStyleExtractor.rPrFont(rPr, false);
            }
            if (pt == null) {
                pt = sizePt(rPr);
            }
            // 第四轮 P1-2：行距单源解析（与提取器同一 spacingLine）；固定磅值也产规则（lineRule+pt），
            // 且任何已声明本层都终止继承——不得穿透取父样式倍数
            if (line == null && style.isSetPPr() && style.getPPr().isSetSpacing()) {
                line = DocxStyleExtractor.spacingLine(style.getPPr().getSpacing());
            }
        }
        // 第五轮 P2-4：链尾仍未取到属性时回退 docDefaults——与提取器同一解析顺序，
        // 否则「仅 docDefaults 声明默认字体」的合法模板会丢规则（生成退回默认值、校验缺期望）
        if (defaults != null) {
            CTRPr defaultRPr = defaults.isSetRPrDefault() ? defaults.getRPrDefault().getRPr() : null;
            if (ascii == null) {
                ascii = DocxStyleExtractor.rPrFont(defaultRPr, true);
            }
            if (eastAsia == null) {
                eastAsia = DocxStyleExtractor.rPrFont(defaultRPr, false);
            }
            if (pt == null) {
                pt = sizePt(defaultRPr);
            }
            if (line == null && defaults.isSetPPrDefault() && defaults.getPPrDefault().getPPr() != null
                    && defaults.getPPrDefault().getPPr().isSetSpacing()) {
                line = DocxStyleExtractor.spacingLine(defaults.getPPrDefault().getPPr().getSpacing());
            }
        }
        // 第四轮 P2-1：两槽各自独立成键，East Asia-only 不再复制填充 ASCII 规则
        Map<String, Object> expect = new LinkedHashMap<>();
        if (ascii != null) {
            expect.put("family", ascii);
        }
        if (eastAsia != null) {
            expect.put("eastAsia", eastAsia);
        }
        if (!expect.isEmpty()) {
            out.add(draft("FONT", selector, heading, config(Map.of("expect", expect, "fix", expect))));
        }
        if (pt != null) {
            out.add(draft("SIZE", selector, heading,
                    config(Map.of("expect", Map.of("pt", pt),
                            "tolerance", Map.of("pt", 0.5),
                            "fix", Map.of("pt", pt)))));
        }
        if (line != null) {
            Map<String, Object> lineExpect = new LinkedHashMap<>();
            if (line.fixed()) {
                lineExpect.put("lineRule", "EXACT".equals(line.kind()) ? "exact" : "atLeast");
                if (line.value() != null) {
                    lineExpect.put("pt", line.value());
                }
            } else {
                lineExpect.put("multiplier", line.value());
            }
            out.add(draft("LINE_SPACING", selector, heading,
                    config(Map.of("expect", lineExpect, "fix", lineExpect))));
        }
    }

    private void emitCaptionRules(List<RuleDraft> out, XWPFDocument doc, XWPFStyles styles) {
        Set<String> seen = new LinkedHashSet<>();
        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            XWPFStyle style = paragraph.getStyle() == null ? null : styles.getStyle(paragraph.getStyle());
            if (style == null || style.getName() == null) {
                continue;
            }
            String name = normalize(style.getName());
            if (!name.contains("caption") && !name.contains("题注")) {
                continue;
            }
            Matcher m = CAPTION_START.matcher(paragraph.getText() == null ? "" : paragraph.getText().strip());
            if (!m.find()) {
                continue;
            }
            boolean perChapter = m.group(3) != null;
            String token = m.group(1);
            String target = "图".equals(token) ? "FIGURE_CAPTION" : "TABLE_CAPTION";
            if (seen.add(target)) {
                // 1106 审查 P2-4：保存可执行 pattern（非章内编号也有实际校验）
                // 复审 P2-2：前缀语义与 CAPTION_START 一致，不强制尾随空白——「图1-2系统架构图」同样合法
                String pattern = "^" + token + "\\s*\\d+" + (perChapter ? "\\s*[-－—.．]\\s*\\d+" : "");
                Map<String, Object> expect = new LinkedHashMap<>();
                expect.put("perChapter", perChapter);
                expect.put("pattern", pattern);
                out.add(new RuleDraft("CAPTION", target, "WARNING", "N",
                        config(Map.of("expect", expect)), 20));
            }
        }
    }

    private void emitCodeRule(List<RuleDraft> out, XWPFStyles styles) {
        for (XWPFStyle style : styles.getStyles()) {
            String name = normalize(style.getName() == null ? "" : style.getName());
            if (name.contains("code") || name.contains("代码")) {
                out.add(new RuleDraft("CODE", "CODE_BLOCK", "WARNING", "N",
                        config(Map.of("expect", Map.of("style", style.getStyleId(), "keepIndent", true))), 30));
                return;
            }
        }
    }

    private void emitReferenceRule(List<RuleDraft> out, XWPFDocument doc) {
        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            if ("参考文献".equals(paragraph.getText() == null ? "" : paragraph.getText().strip())) {
                out.add(new RuleDraft("REFERENCE", "REFERENCE", "WARNING", "N",
                        config(Map.of("expect", Map.of("standard", "GB/T 7714"))), 40));
                return;
            }
        }
    }

    /** 第五轮 P2-3：pgMar 与 pgSz 独立读取——pgSz-only 模板也产出纸型规则，部分页边距不拖累其余键。 */
    private void emitPageRules(List<RuleDraft> out, XWPFDocument doc) {
        var body = doc.getDocument().getBody();
        if (!body.isSetSectPr()) {
            return;
        }
        var sectPr = body.getSectPr();
        Map<String, Object> expect = new LinkedHashMap<>();
        if (sectPr.isSetPgMar()) {
            CTPageMar mar = sectPr.getPgMar();
            putIfPresent(expect, "top", mar.getTop());
            putIfPresent(expect, "bottom", mar.getBottom());
            putIfPresent(expect, "left", mar.getLeft());
            putIfPresent(expect, "right", mar.getRight());
        }
        if (sectPr.isSetPgSz()) {
            putIfPresent(expect, "width", sectPr.getPgSz().getW());
            putIfPresent(expect, "height", sectPr.getPgSz().getH());
        }
        if (!expect.isEmpty()) {
            expect.put("unit", "mm");
            out.add(new RuleDraft("MARGIN", "PAGE", "BLOCKER", "Y",
                    config(Map.of("expect", expect, "fix", expect)), 13));
        }
        if (!doc.getHeaderList().isEmpty()) {
            out.add(new RuleDraft("HEADER_FOOTER", "HEADER", "WARNING", "N",
                    config(Map.of("expect", Map.of("present", true))), 14));
        }
        if (!doc.getFooterList().isEmpty()) {
            out.add(new RuleDraft("HEADER_FOOTER", "FOOTER", "WARNING", "N",
                    config(Map.of("expect", Map.of("present", true))), 14));
        }
    }

    private static void putIfPresent(Map<String, Object> into, String key, Object twips) {
        Double mm = twipsToMm(twips);
        if (mm != null) {
            into.put(key, mm);
        }
    }

    /** 复审 P1-1：返回 styleId 以便走 basedOn 继承链（DocxStyleExtractor.styleChain）。 */
    private static String findStyleIdByName(XWPFStyles styles, String normalizedName) {
        for (XWPFStyle style : styles.getStyles()) {
            String name = normalize(style.getName() == null ? "" : style.getName());
            if (name.replace("标题", "heading").equals(normalizedName)) {
                return style.getStyleId();
            }
        }
        return null;
    }

    private static CTDocDefaults defaults(XWPFStyles styles) {
        return styles.getCtStyles() != null && styles.getCtStyles().isSetDocDefaults()
                ? styles.getCtStyles().getDocDefaults() : null;
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static Double sizePt(CTRPr rPr) {
        if (rPr == null || rPr.sizeOfSzArray() == 0) {
            return null;
        }
        Double half = num(rPr.getSzArray(0).getVal());
        return half == null ? null : round2(half / 2);
    }

    /** dxa（1/20 磅）→ 毫米，保留两位小数。 */
    private static Double twipsToMm(Object twips) {
        Double value = num(twips);
        return value == null ? null : round2(value * 25.4 / 1440);
    }

    private static Double num(Object value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private RuleDraft draft(String type, String selector, boolean heading, String json) {
        return new RuleDraft(type, selector, heading ? "BLOCKER" : "WARNING", "Y", json, orderOf(type));
    }

    private static int orderOf(String ruleType) {
        return switch (ruleType) {
            case "FONT" -> 10;
            case "SIZE" -> 11;
            case "LINE_SPACING" -> 12;
            default -> 0;
        };
    }

    private String config(Map<String, Object> payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new TemplateParseException("规则 JSON 序列化失败", e);
        }
    }
}
