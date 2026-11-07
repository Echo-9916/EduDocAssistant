package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.template.domain.StyleSnapshot;
import com.cidas.edudoc.template.domain.StyleSnapshot.LineSpacing;
import com.cidas.edudoc.template.domain.StyleSnapshot.PageFacts;
import com.cidas.edudoc.template.domain.StyleSnapshot.RunFact;
import com.cidas.edudoc.template.domain.StyleSnapshot.ParagraphFact;
import com.cidas.edudoc.template.domain.TemplateParseException;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDocDefaults;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * docx 样式事实提取（D-04）：段落实际值按 Word 渲染语义解析——
 * run 显式属性 → 段落样式（含 basedOn 继承链，1106 审查 P1-1）→ docDefaults；
 * 行距无设置时按 Word 默认单倍（1.0）参与比对，EXACT/AT_LEAST 固定磅值不参与倍数比对。
 * 选择器词表与规则 target_selector 对齐；页边距/页眉页脚/纸型宽高读 sectPr。
 */
@Component
public class DocxStyleExtractor {

    private static final Pattern CAPTION_TOKEN = Pattern.compile("^([图表])\\s*\\d+\\s*[-－.]\\s*\\d+");
    private static final Pattern HEADING_NAME = Pattern.compile("^heading\\s+(\\d)$");
    private static final int MAX_BASED_ON_DEPTH = 5;

    public StyleSnapshot extract(byte[] docxBytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            XWPFStyles styles = doc.getStyles();
            List<ParagraphFact> facts = new ArrayList<>();
            int bodyIndex = 0;
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    facts.add(paragraphFact(styles, paragraph, bodyIndex));
                }
                bodyIndex++;
            }
            return new StyleSnapshot(facts, pageFacts(doc));
        } catch (Exception e) {
            throw new TemplateParseException("样式事实提取失败：" + e.getMessage(), e);
        }
    }

    private ParagraphFact paragraphFact(XWPFStyles styles, XWPFParagraph paragraph, int index) {
        String styleId = paragraph.getStyle();
        List<CTStyle> chain = styleChain(styles, styleId);
        String styleName = styleName(styles, styleId);
        String text = paragraph.getText() == null ? "" : paragraph.getText().strip();
        String selector = classify(styleName, text);

        // 第五轮 P2-5：继承层（样式链→docDefaults）与 run 显式层分离；比对按逐 run 生效值，
        // family()/pt() 等兼容读取仍为「首个显式 ?: 继承」
        String inheritedFamily = null;
        String inheritedEastAsia = null;
        Double inheritedPt = null;
        CTDocDefaults defaults = styles == null || styles.getCtStyles() == null
                ? null : styles.getCtStyles().getDocDefaults();
        for (CTStyle style : chain) {
            CTRPr rPr = style.isSetRPr() ? style.getRPr() : null;
            if (inheritedFamily == null) {
                inheritedFamily = rPrFont(rPr, true);
            }
            if (inheritedEastAsia == null) {
                inheritedEastAsia = rPrFont(rPr, false);
            }
            if (inheritedPt == null) {
                inheritedPt = rPrSizePt(rPr);
            }
        }
        if (defaults != null && defaults.isSetRPrDefault()) {
            CTRPr defaultRPr = defaults.getRPrDefault().getRPr();
            if (inheritedFamily == null) {
                inheritedFamily = rPrFont(defaultRPr, true);
            }
            if (inheritedEastAsia == null) {
                inheritedEastAsia = rPrFont(defaultRPr, false);
            }
            if (inheritedPt == null) {
                inheritedPt = rPrSizePt(defaultRPr);
            }
        }
        List<RunFact> runs = new ArrayList<>();
        int cursor = 0;
        for (XWPFRun run : paragraph.getRuns()) {
            String runText = run.getText(0) == null ? "" : run.getText(0);
            String runFamily = run.getFontFamily() != null && !run.getFontFamily().isBlank()
                    ? run.getFontFamily() : null;
            Double runPt = run.getFontSizeAsDouble() != null && run.getFontSizeAsDouble() > 0
                    ? run.getFontSizeAsDouble() : null;
            runs.add(new RunFact(cursor, cursor + runText.length(), runText,
                    runFamily, runEastAsiaFamily(run), runPt));
            cursor += runText.length();
        }
        LineSpacing line = resolveLine(paragraph, chain, defaults);
        return new ParagraphFact(index, selector, inheritedFamily, inheritedEastAsia, inheritedPt,
                line, text, runs);
    }

    /**
     * 行距解析顺序：段落显式 line/lineRule → 样式继承链 → docDefaults → Word 默认单倍（AUTO 1.0）。
     * 第四轮 P1-2：固定磅值与「未设置」不再共用 null（LineSpacing.kind 显式区分）；
     * 复审 P1-2：仅段前/段后（未设 line）不截断继承。
     */
    private static LineSpacing resolveLine(XWPFParagraph paragraph, List<CTStyle> chain, CTDocDefaults defaults) {
        if (paragraph.getCTP().isSetPPr() && paragraph.getCTP().getPPr().isSetSpacing()) {
            LineSpacing value = spacingLine(paragraph.getCTP().getPPr().getSpacing());
            if (value != null) {
                return value;
            }
        }
        for (CTStyle style : chain) {
            if (style.isSetPPr() && style.getPPr().isSetSpacing()) {
                LineSpacing value = spacingLine(style.getPPr().getSpacing());
                if (value != null) {
                    return value;
                }
            }
        }
        if (defaults != null && defaults.isSetPPrDefault() && defaults.getPPrDefault().getPPr() != null
                && defaults.getPPrDefault().getPPr().isSetSpacing()) {
            LineSpacing value = spacingLine(defaults.getPPrDefault().getPPr().getSpacing());
            if (value != null) {
                return value;
            }
        }
        return new LineSpacing("AUTO", 1.0); // 无任何显式 line 设置 = Word 默认单倍行距
    }

    /** basedOn 继承链（1106 审查 P1-1）：无 pStyle 段回退 Normal 样式；链深 ≤5 且防环。 */
    static List<CTStyle> styleChain(XWPFStyles styles, String styleId) {
        List<CTStyle> chain = new ArrayList<>();
        if (styles == null) {
            return chain;
        }
        String current = styleId != null ? styleId : findNormalStyleId(styles);
        Set<String> visited = new HashSet<>();
        for (int depth = 0; current != null && depth < MAX_BASED_ON_DEPTH; depth++) {
            if (!visited.add(current)) {
                break;
            }
            XWPFStyle style = styles.getStyle(current);
            if (style == null || style.getCTStyle() == null) {
                break;
            }
            chain.add(style.getCTStyle());
            CTStyle ct = style.getCTStyle();
            current = ct.isSetBasedOn() && ct.getBasedOn().getVal() != null
                    ? String.valueOf(ct.getBasedOn().getVal()) : null;
        }
        return chain;
    }

    private static String findNormalStyleId(XWPFStyles styles) {
        for (XWPFStyle style : styles.getStyles()) {
            String name = style.getName() == null ? "" : style.getName().trim().toLowerCase(Locale.ROOT);
            if (name.equals("normal") || name.equals("正文")) {
                return style.getStyleId();
            }
        }
        return null;
    }

    /** 复审 P1-1：ascii=true 取西文槽，false 取 East Asia 中文槽——两槽独立提取、独立比对。 */
    static String rPrFont(CTRPr rPr, boolean ascii) {
        if (rPr == null || rPr.sizeOfRFontsArray() == 0) {
            return null;
        }
        CTFonts fonts = rPr.getRFontsArray(0);
        Object value = ascii ? fonts.getAscii() : fonts.getEastAsia();
        String text = value == null ? null : String.valueOf(value);
        return text == null || "null".equals(text) || text.isBlank() ? null : text;
    }

    private static String runEastAsiaFamily(XWPFRun run) {
        if (run.getCTR() == null || !run.getCTR().isSetRPr()) {
            return null;
        }
        return rPrFont(run.getCTR().getRPr(), false);
    }

    private static Double rPrSizePt(CTRPr rPr) {
        if (rPr == null || rPr.sizeOfSzArray() == 0) {
            return null;
        }
        Object val = rPr.getSzArray(0).getVal();
        if (val == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(val)) / 2;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String classify(String styleName, String text) {
        String name = styleName == null ? "" : styleName;
        Matcher heading = HEADING_NAME.matcher(name.replace("标题", "heading"));
        if (heading.matches()) {
            return "HEADING_" + heading.group(1);
        }
        boolean captionStyle = name.contains("caption") || name.contains("题注");
        Matcher token = CAPTION_TOKEN.matcher(text);
        if (captionStyle || token.find()) {
            boolean table = text.stripLeading().startsWith("表");
            return table ? "TABLE_CAPTION" : "FIGURE_CAPTION";
        }
        if (name.contains("code") || name.contains("代码")) {
            return "CODE_BLOCK";
        }
        if (name.isEmpty() || name.contains("normal") || name.contains("正文")) {
            return "BODY";
        }
        return "OTHER";
    }

    private static String styleName(XWPFStyles styles, String styleId) {
        if (styles == null || styleId == null) {
            return null;
        }
        XWPFStyle style = styles.getStyle(styleId);
        return style == null || style.getName() == null
                ? null : style.getName().trim().toLowerCase(Locale.ROOT);
    }

    /** 本层显式设置了 line 或固定 lineRule → LineSpacing；仅 before/after（未设 line）→ null 继续上溯。 */
    static LineSpacing spacingLine(CTSpacing spacing) {
        String rule = spacing.isSetLineRule()
                ? spacing.getLineRule().toString().toLowerCase(Locale.ROOT) : "auto";
        boolean fixed = rule.startsWith("exact") || rule.startsWith("atleast");
        if (!spacing.isSetLine()) {
            // EXACT/AT_LEAST 但无磅值：语义仍是固定（不可比倍数），照常终止继承
            return fixed ? new LineSpacing(rule.startsWith("exact") ? "EXACT" : "AT_LEAST", null) : null;
        }
        try {
            double line = Double.parseDouble(String.valueOf(spacing.getLine()));
            if (line <= 0) {
                return fixed ? new LineSpacing(ruleKind(rule), null) : null;
            }
            return fixed
                    ? new LineSpacing(ruleKind(rule), round2(line / 20))   // 固定磅值：twips→pt
                    : new LineSpacing("AUTO", Math.round(line / 240 * 100) / 100.0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String ruleKind(String rule) {
        return rule.startsWith("exact") ? "EXACT" : "AT_LEAST";
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static PageFacts pageFacts(XWPFDocument doc) {
        var body = doc.getDocument().getBody();
        Double top = null;
        Double bottom = null;
        Double left = null;
        Double right = null;
        Double width = null;
        Double height = null;
        if (body.isSetSectPr()) {
            CTSectPr sectPr = body.getSectPr();
            if (sectPr.isSetPgMar()) {
                var mar = sectPr.getPgMar();
                top = twipsToMm(mar.getTop());
                bottom = twipsToMm(mar.getBottom());
                left = twipsToMm(mar.getLeft());
                right = twipsToMm(mar.getRight());
            }
            if (sectPr.isSetPgSz()) {
                width = twipsToMm(sectPr.getPgSz().getW());
                height = twipsToMm(sectPr.getPgSz().getH());
            }
        }
        return new PageFacts(top, bottom, left, right, width, height,
                !doc.getHeaderList().isEmpty(), !doc.getFooterList().isEmpty());
    }

    private static Double twipsToMm(Object twips) {
        if (twips == null) {
            return null;
        }
        try {
            return Math.round(Double.parseDouble(String.valueOf(twips)) * 25.4 / 1440 * 100) / 100.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
