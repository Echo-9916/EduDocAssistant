package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.template.domain.RuleConfig;
import com.cidas.edudoc.template.domain.TemplateParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
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

            for (String[] spec : SKELETON) {
                int level = Integer.parseInt(spec[0].substring(spec[0].lastIndexOf('_') + 1));
                XWPFParagraph heading = doc.createParagraph();
                heading.setStyle("Heading" + level);
                heading.createRun().setText(spec[1]);
                doc.createParagraph().createRun().setText("（正文占位）");
            }
            if (outline.headerPresent) {
                doc.createHeader(HeaderFooterType.DEFAULT);
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
                    facts.family = config.family();
                    facts.eastAsia = config.eastAsia();
                }
                case "SIZE" -> headingFacts(outline, rule.getTargetSelector()).pt = config.pt();
                case "LINE_SPACING" -> {
                    RuleFacts facts = headingFacts(outline, rule.getTargetSelector());
                    if (config.multiplier() != null) {
                        facts.lineKind = "AUTO";
                        facts.lineValue = config.multiplier();
                    } else if (config.fixedLine() != null) {
                        facts.lineKind = config.fixedLine().kind();
                        facts.lineValue = config.fixedLine().value();
                    }
                }
                case "MARGIN" -> outline.margins.putAll(config.pageValues());
                case "HEADER_FOOTER" -> {
                    if ("HEADER".equals(rule.getTargetSelector())) {
                        outline.headerPresent = config.present();
                    }
                }
                default -> {
                    // 其余规则不影响生成
                }
            }
        }
        return outline;
    }

    /** 终审 I6 纵深防御：仅 HEADING_1..3 计入；异常 selector 归入 0 层（生成时忽略）。 */
    private static RuleFacts headingFacts(Outline outline, String selector) {
        int level = selector != null && selector.matches("HEADING_[1-3]")
                ? Integer.parseInt(selector.substring(selector.lastIndexOf('_') + 1)) : 0;
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
    }
}
