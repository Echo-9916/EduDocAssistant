package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.template.domain.RuleConfig;
import com.cidas.edudoc.template.domain.TemplateParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按模板规则生成大纲 .docx（D-07/J4）：标题层级按模板的字体/字号/行距建段落样式，页面设置按 MARGIN 规则，
 * 「按模板新建」的产物即合规起点。ponytail: 生成封面占位与章节标题骨架，具体正文由学生填写。
 */
@Component
@RequiredArgsConstructor
public class TemplateOutlineGenerator {

    private static final String[][] SKELETON = {
            {"HEADING_1", "1 绪论"}, {"HEADING_2", "1.1 编写目的"}, {"HEADING_1", "2 设计与实现"},
            {"HEADING_1", "3 结论"},
    };

    private final ObjectMapper mapper;

    public byte[] generate(String title, List<FormatRule> rules) {
        try (XWPFDocument doc = new XWPFDocument()) {
            Outline outline = readOutline(rules);
            XWPFStyles styles = doc.createStyles();
            for (int level = 1; level <= 3; level++) {
                styles.addStyle(headingStyle("Heading" + level, level, outline));
            }

            XWPFParagraph cover = doc.createParagraph();
            cover.createRun().setText(title);
            applyBodyFormat(cover, outline);

            for (String[] spec : SKELETON) {
                int level = Integer.parseInt(spec[0].substring(spec[0].lastIndexOf('_') + 1));
                XWPFParagraph heading = doc.createParagraph();
                heading.setStyle("Heading" + level);
                heading.createRun().setText(spec[1]);
                XWPFParagraph body = doc.createParagraph();
                body.createRun().setText("（正文占位）");
                // 正文占位/封面也必须满足 BODY 规则，否则「按模板新建」后立刻校验就会出一串 WARNING
                applyBodyFormat(body, outline);
            }
            if (outline.headerPresent) {
                doc.createHeader(HeaderFooterType.DEFAULT);
            }
            if (outline.footerPresent) {
                doc.createFooter(HeaderFooterType.DEFAULT);
            }
            applyMargins(doc, outline);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new TemplateParseException("模板大纲生成失败：" + e.getMessage(), e);
        }
    }

    private XWPFStyle headingStyle(String id, int level, Outline outline) {
        CTStyle ct = CTStyle.Factory.newInstance();
        ct.setStyleId(id);
        ct.setType(STStyleType.PARAGRAPH);
        ct.addNewName().setVal("heading " + level);
        RuleFacts facts = outline.heading.getOrDefault(level, new RuleFacts());
        CTFonts fonts = ct.addNewRPr().addNewRFonts();
        // 第四轮 P2-1：缺侧用「安全默认」而非另一槽伪装模板期望（ascii=Times、eastAsia=黑体是排版惯例默认）
        fonts.setAscii(facts.family != null ? facts.family : "Times New Roman");
        fonts.setEastAsia(facts.eastAsia != null ? facts.eastAsia : "黑体");
        double pt = facts.pt == null ? (level == 1 ? 16 : 14) : facts.pt;
        ct.getRPr().addNewSz().setVal(BigInteger.valueOf(Math.round(pt * 2)));
        // 第四轮 P1-2：固定行距按规则保真生成（EXACT/AT_LEAST+磅值），无规则才回退 1.5 倍
        CTSpacing spacing = ct.addNewPPr().addNewSpacing();
        if ("EXACT".equals(facts.lineKind) || "AT_LEAST".equals(facts.lineKind)) {
            spacing.setLineRule("EXACT".equals(facts.lineKind)
                    ? STLineSpacingRule.EXACT : STLineSpacingRule.AT_LEAST);
            spacing.setLine(BigInteger.valueOf(Math.round((facts.lineValue == null ? 20 : facts.lineValue) * 20)));
        } else {
            spacing.setLineRule(STLineSpacingRule.AUTO);
            spacing.setLine(BigInteger.valueOf(
                    Math.round((facts.lineValue == null ? 1.5 : facts.lineValue) * 240)));
        }
        return new XWPFStyle(ct);
    }

    /** 导出/临时校验 DOCX 复用：按 selector（HEADING_1..3 / BODY）把规则直接写到段落 run。 */
    public void applyParagraphFormat(XWPFParagraph paragraph, List<FormatRule> rules, String selector) {
        Outline outline = readOutline(rules);
        applyFacts(paragraph, headingFacts(outline, selector));
    }

    /** BODY 规则（readOutline 中 selector 非 HEADING 的规则都落在 key=0）应用到封面/正文占位。 */
    private void applyBodyFormat(XWPFParagraph paragraph, Outline outline) {
        applyFacts(paragraph, outline.heading.get(0));
    }

    private void applyFacts(XWPFParagraph paragraph, RuleFacts facts) {
        if (facts == null) {
            return;
        }
        XWPFRun run = paragraph.getRuns().isEmpty() ? paragraph.createRun() : paragraph.getRuns().get(0);
        if (facts.family != null) {
            run.setFontFamily(facts.family, XWPFRun.FontCharRange.ascii);
        }
        if (facts.eastAsia != null) {
            run.setFontFamily(facts.eastAsia, XWPFRun.FontCharRange.eastAsia);
        }
        if (facts.pt != null) {
            run.setFontSize(facts.pt);
        }
        if (facts.lineKind != null) {
            var pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
            var spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
            if ("EXACT".equals(facts.lineKind) || "AT_LEAST".equals(facts.lineKind)) {
                spacing.setLineRule("EXACT".equals(facts.lineKind)
                        ? STLineSpacingRule.EXACT : STLineSpacingRule.AT_LEAST);
                spacing.setLine(BigInteger.valueOf(
                        Math.round((facts.lineValue == null ? 20 : facts.lineValue) * 20)));
            } else {
                spacing.setLineRule(STLineSpacingRule.AUTO);
                spacing.setLine(BigInteger.valueOf(
                        Math.round((facts.lineValue == null ? 1.5 : facts.lineValue) * 240)));
            }
        }
    }

    private void applyMargins(XWPFDocument doc, Outline outline) {
        var body = doc.getDocument().getBody();
        var sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTPageMar mar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        Map<String, Double> mm = outline.margins;
        mar.setTop(dxa(mm.getOrDefault("top", 25.4)));
        mar.setBottom(dxa(mm.getOrDefault("bottom", 25.4)));
        mar.setLeft(dxa(mm.getOrDefault("left", 31.75)));
        mar.setRight(dxa(mm.getOrDefault("right", 25.4)));
        // 1106 审查 P2-2：纸型显式写入（规则给定宽高按规则，否则 A4 兜底——按模板新建必须保证 A4）
        var pgSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pgSz.setW(dxa(mm.getOrDefault("width", 210.0)));
        pgSz.setH(dxa(mm.getOrDefault("height", 297.0)));
    }

    private static BigInteger dxa(double mm) {
        return BigInteger.valueOf(Math.round(mm * 1440 / 25.4));
    }

    /** 第五轮：规则取值经 {@link RuleConfig} 单点解释（与保存校验/比对/修复同源），非法配置跳过该规则。 */
    private Outline readOutline(List<FormatRule> rules) {
        Outline outline = new Outline();
        for (FormatRule rule : rules) {
            RuleConfig config;
            try {
                config = RuleConfig.parse(rule.getRuleType(), rule.getRuleConfigJson(), mapper);
            } catch (IllegalArgumentException invalid) {
                continue;
            }
            switch (rule.getRuleType()) {
                case "FONT" -> {
                    RuleFacts facts = headingFacts(outline, rule.getTargetSelector());
                    if (facts != null) {
                        facts.family = config.family();
                        facts.eastAsia = config.eastAsia();
                    }
                }
                case "SIZE" -> {
                    RuleFacts facts = headingFacts(outline, rule.getTargetSelector());
                    if (facts != null) {
                        facts.pt = config.pt();
                    }
                }
                case "LINE_SPACING" -> {
                    RuleFacts facts = headingFacts(outline, rule.getTargetSelector());
                    if (facts != null && config.multiplier() != null) {
                        facts.lineKind = "AUTO";
                        facts.lineValue = config.multiplier();
                    } else if (facts != null && config.fixedLine() != null) {
                        facts.lineKind = config.fixedLine().kind();
                        facts.lineValue = config.fixedLine().value();
                    }
                }
                case "MARGIN" -> outline.margins.putAll(config.pageValues());
                case "HEADER_FOOTER" -> {
                    if ("HEADER".equals(rule.getTargetSelector())) {
                        outline.headerPresent = config.present();
                    } else if ("FOOTER".equals(rule.getTargetSelector())) {
                        outline.footerPresent = config.present();
                    }
                }
                default -> {
                    // 其余规则不影响生成
                }
            }
        }
        return outline;
    }

    /**
     * 只收集 HEADING_1..3 与 BODY 的规则：题注/代码/其它选择器不参与生成，
     * 否则会把 CODE 的等宽字体等规则错误套到正文上。BODY 落 key=0。
     */
    private static RuleFacts headingFacts(Outline outline, String selector) {
        int level = selector != null && selector.matches("HEADING_[1-3]")
                ? Integer.parseInt(selector.substring(selector.lastIndexOf('_') + 1)) : 0;
        if (level == 0 && !"BODY".equals(selector)) {
            return null;
        }
        return outline.heading.computeIfAbsent(level, k -> new RuleFacts());
    }

    private static final class RuleFacts {
        String family;
        String eastAsia;
        Double pt;
        String lineKind;
        Double lineValue;
    }

    private static final class Outline {
        final Map<Integer, RuleFacts> heading = new HashMap<>();
        final Map<String, Double> margins = new HashMap<>();
        boolean headerPresent;
        boolean footerPresent;
    }
}
