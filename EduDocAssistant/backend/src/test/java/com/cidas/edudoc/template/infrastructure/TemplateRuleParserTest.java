package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.template.domain.RuleConfig;
import com.cidas.edudoc.template.domain.RuleDraft;
import com.cidas.edudoc.template.domain.TemplateParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-02 Word 模板样式 → 规则集解析（详细设计 §3.3.1 规则 JSON 契约）。 */
class TemplateRuleParserTest {

    private final TemplateRuleParser parser = new TemplateRuleParser(new ObjectMapper());
    private final ObjectMapper mapper = new ObjectMapper();

    private static XWPFStyle paragraphStyle(String id, String name, String font, int halfPoints, int line240) {
        CTStyle ct = CTStyle.Factory.newInstance();
        ct.setStyleId(id);
        ct.setType(STStyleType.PARAGRAPH);
        ct.addNewName().setVal(name);
        if (font != null) {
            ct.addNewRPr().addNewRFonts().setAscii(font);
        }
        if (halfPoints > 0) {
            ct.getRPr().addNewSz().setVal(BigInteger.valueOf(halfPoints));
        }
        if (line240 > 0) {
            CTSpacing spacing = ct.addNewPPr().addNewSpacing();
            spacing.setLineRule(STLineSpacingRule.AUTO);
            spacing.setLine(BigInteger.valueOf(line240));
        }
        return new XWPFStyle(ct);
    }

    private byte[] templateBytes() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFStyles styles = doc.createStyles();
            styles.addStyle(paragraphStyle("H1", "heading 1", "黑体", 32, 360));
            styles.addStyle(paragraphStyle("Normal", "Normal", "宋体", 24, 0));
            styles.addStyle(paragraphStyle("Cap", "caption", null, 0, 0));
            styles.addStyle(paragraphStyle("Code", "代码", "Consolas", 20, 0));

            doc.createParagraph().setStyle("H1");
            doc.createParagraph().createRun().setText("参考文献");
            var caption = doc.createParagraph();
            caption.setStyle("Cap");
            caption.createRun().setText("图1-2 系统架构图");
            var code = doc.createParagraph();
            code.setStyle("Code");
            doc.createHeader(HeaderFooterType.DEFAULT);

            var sectPr = doc.getDocument().getBody().isSetSectPr()
                    ? doc.getDocument().getBody().getSectPr()
                    : doc.getDocument().getBody().addNewSectPr();
            sectPr.addNewPgSz().setW(BigInteger.valueOf(11906));
            sectPr.getPgSz().setH(BigInteger.valueOf(16838));
            CTPageMar mar = sectPr.addNewPgMar();
            mar.setTop(BigInteger.valueOf(1440));
            mar.setBottom(BigInteger.valueOf(1440));
            mar.setLeft(BigInteger.valueOf(1800));
            mar.setRight(BigInteger.valueOf(1440));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private RuleDraft find(List<RuleDraft> rules, String type, String selector) {
        return rules.stream()
                .filter(r -> r.ruleType().equals(type) && r.targetSelector().equals(selector))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少规则 " + type + "/" + selector + "，实际=" + rules));
    }

    @Test
    void parsesHeadingBodySizeSpacingMargin() throws Exception {
        List<RuleDraft> rules = parser.parse(templateBytes());

        JsonNode h1Font = mapper.readTree(find(rules, "FONT", "HEADING_1").ruleConfigJson());
        assertEquals("黑体", h1Font.path("expect").path("family").asText());
        assertEquals("黑体", h1Font.path("fix").path("family").asText());
        assertEquals("BLOCKER", find(rules, "FONT", "HEADING_1").severity());
        assertEquals("Y", find(rules, "FONT", "HEADING_1").autoFixable());

        JsonNode h1Size = mapper.readTree(find(rules, "SIZE", "HEADING_1").ruleConfigJson());
        assertEquals(16.0, h1Size.path("expect").path("pt").asDouble(), 0.001);

        JsonNode h1Spacing = mapper.readTree(find(rules, "LINE_SPACING", "HEADING_1").ruleConfigJson());
        assertEquals(1.5, h1Spacing.path("expect").path("multiplier").asDouble(), 0.001);

        JsonNode bodyFont = mapper.readTree(find(rules, "FONT", "BODY").ruleConfigJson());
        assertEquals("宋体", bodyFont.path("expect").path("family").asText());
        assertEquals("WARNING", find(rules, "FONT", "BODY").severity());

        RuleDraft margin = find(rules, "MARGIN", "PAGE");
        JsonNode marginJson = mapper.readTree(margin.ruleConfigJson());
        assertEquals(25.4, marginJson.path("expect").path("top").asDouble(), 0.01);
        assertEquals(31.75, marginJson.path("expect").path("left").asDouble(), 0.01);
        assertEquals(210.0, marginJson.path("expect").path("width").asDouble(), 0.6);
        assertEquals(297.0, marginJson.path("expect").path("height").asDouble(), 0.6);
        assertEquals("BLOCKER", margin.severity());

        // 规则顺序单调：FONT < SIZE < LINE_SPACING < MARGIN < CAPTION < CODE < REFERENCE
        assertTrue(find(rules, "FONT", "BODY").ruleOrder() < find(rules, "REFERENCE", "REFERENCE").ruleOrder());
    }

    @Test
    void parsesCaptionCodeReferenceAndHeaderRules() throws Exception {
        List<RuleDraft> rules = parser.parse(templateBytes());

        RuleDraft caption = find(rules, "CAPTION", "FIGURE_CAPTION");
        assertEquals("N", caption.autoFixable());
        JsonNode captionJson = mapper.readTree(caption.ruleConfigJson());
        assertTrue(captionJson.path("expect").path("perChapter").asBoolean());
        // 1106 审查 P2-4：规则带可执行 pattern
        assertTrue(captionJson.path("expect").path("pattern").asText().startsWith("^图"));

        assertNotNull(find(rules, "CODE", "CODE_BLOCK"));
        assertNotNull(find(rules, "REFERENCE", "REFERENCE"));
        assertNotNull(find(rules, "HEADER_FOOTER", "HEADER"));
    }

    /** 复审 P1-1：模板 Heading 经 basedOn 继承 Normal 字体时也必须产出 FONT 规则。 */
    @Test
    void headingInheritsFontViaBasedOn() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFStyles styles = doc.createStyles();
            CTStyle normal = CTStyle.Factory.newInstance();
            normal.setStyleId("Normal");
            normal.setType(STStyleType.PARAGRAPH);
            normal.addNewName().setVal("Normal");
            normal.addNewRPr().addNewRFonts().setAscii("宋体");
            styles.addStyle(new XWPFStyle(normal));

            CTStyle heading = CTStyle.Factory.newInstance();
            heading.setStyleId("H1");
            heading.setType(STStyleType.PARAGRAPH);
            heading.addNewName().setVal("heading 1");
            heading.addNewBasedOn().setVal("Normal");
            heading.addNewRPr().addNewSz().setVal(BigInteger.valueOf(32));
            styles.addStyle(new XWPFStyle(heading));

            doc.createParagraph().setStyle("H1");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            List<RuleDraft> rules = parser.parse(out.toByteArray());
            RuleDraft font = rules.stream()
                    .filter(r -> r.ruleType().equals("FONT") && r.targetSelector().equals("HEADING_1"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("basedOn 继承的字体规则缺失：" + rules));
            org.junit.jupiter.api.Assertions.assertEquals("宋体",
                    mapper.readTree(font.ruleConfigJson()).path("expect").path("family").asText());
        }
    }

    /** 复审 P1-1：模板同时设 ascii 与 East Asia 时，FONT 规则保存两个槽。 */
    @Test
    void fontRuleCarriesEastAsiaSlot() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFStyles styles = doc.createStyles();
            CTStyle h1 = CTStyle.Factory.newInstance();
            h1.setStyleId("H1");
            h1.setType(STStyleType.PARAGRAPH);
            h1.addNewName().setVal("heading 1");
            h1.addNewRPr().addNewRFonts().setAscii("Times New Roman");
            h1.getRPr().getRFontsArray(0).setEastAsia("黑体");
            styles.addStyle(new XWPFStyle(h1));
            doc.createParagraph().setStyle("H1");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            List<RuleDraft> rules = parser.parse(out.toByteArray());
            RuleDraft font = rules.stream()
                    .filter(r -> r.ruleType().equals("FONT") && r.targetSelector().equals("HEADING_1"))
                    .findFirst().orElseThrow();
            var expect = mapper.readTree(font.ruleConfigJson()).path("expect");
            org.junit.jupiter.api.Assertions.assertEquals("Times New Roman", expect.path("family").asText());
            org.junit.jupiter.api.Assertions.assertEquals("黑体", expect.path("eastAsia").asText());
        }
    }

    /** 第三轮 P1-2：子样式 EXACT 固定行距是继承终止结果，不得穿透到父样式的倍数。 */
    @Test
    void childExactLineStopsInheritanceFromParentMultiplier() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFStyles styles = doc.createStyles();
            CTStyle normal = CTStyle.Factory.newInstance();
            normal.setStyleId("Normal");
            normal.setType(STStyleType.PARAGRAPH);
            normal.addNewName().setVal("Normal");
            CTSpacing normalSpacing = normal.addNewPPr().addNewSpacing();
            normalSpacing.setLineRule(STLineSpacingRule.AUTO);
            normalSpacing.setLine(BigInteger.valueOf(360));
            styles.addStyle(new XWPFStyle(normal));

            CTStyle h1 = CTStyle.Factory.newInstance();
            h1.setStyleId("H1");
            h1.setType(STStyleType.PARAGRAPH);
            h1.addNewName().setVal("heading 1");
            h1.addNewBasedOn().setVal("Normal");
            CTSpacing h1Spacing = h1.addNewPPr().addNewSpacing();
            h1Spacing.setLineRule(STLineSpacingRule.EXACT);
            h1Spacing.setLine(BigInteger.valueOf(480));
            styles.addStyle(new XWPFStyle(h1));
            doc.createParagraph().setStyle("H1");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            List<RuleDraft> rules = parser.parse(out.toByteArray());
            var line = rules.stream().filter(r -> r.ruleType().equals("LINE_SPACING")
                    && r.targetSelector().equals("HEADING_1")).findFirst()
                    .orElseThrow(() -> new AssertionError("子样式固定行距应产出自身规则：" + rules));
            var expect = mapper.readTree(line.ruleConfigJson()).path("expect");
            // 第四轮 P1-2：本层 EXACT 终止继承并产出固定表达——绝不得穿透取父样式 1.5 倍
            org.junit.jupiter.api.Assertions.assertEquals("exact", expect.path("lineRule").asText());
            org.junit.jupiter.api.Assertions.assertEquals(24.0, expect.path("pt").asDouble(), 0.01);
            assertFalse(expect.has("multiplier"));
        }
    }

    /** 第五轮 P2-3：pgSz-only 模板（无 pgMar）也必须产出纸型规则。 */
    @Test
    void pageSizeOnlyTemplateStillProducesPageRule() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            var sectPr = doc.getDocument().getBody().addNewSectPr();
            sectPr.addNewPgSz().setW(BigInteger.valueOf(11906));
            sectPr.getPgSz().setH(BigInteger.valueOf(16838));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            List<RuleDraft> rules = parser.parse(out.toByteArray());
            RuleDraft margin = find(rules, "MARGIN", "PAGE");
            var expect = mapper.readTree(margin.ruleConfigJson()).path("expect");
            assertEquals(210.0, expect.path("width").asDouble(), 0.6);
            assertEquals(297.0, expect.path("height").asDouble(), 0.6);
            assertFalse(expect.has("top"), "无 pgMar 不得伪造边距期望");
        }
    }

    /** 第五轮 P2-4：仅 docDefaults 声明默认字体字号时，标题/正文规则仍须产出（与提取器同回退序）。 */
    @Test
    void docDefaultsFallThroughProducesRules() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFStyles styles = doc.createStyles();
            var ctStyles = styles.getCtStyles();
            ctStyles.addNewDocDefaults().addNewRPrDefault().addNewRPr()
                    .addNewRFonts().setAscii("Arial");
            ctStyles.getDocDefaults().getRPrDefault().getRPr()
                    .addNewSz().setVal(BigInteger.valueOf(24));
            CTStyle h1 = CTStyle.Factory.newInstance();
            h1.setStyleId("H1");
            h1.setType(STStyleType.PARAGRAPH);
            h1.addNewName().setVal("heading 1");
            styles.addStyle(new XWPFStyle(h1));
            doc.createParagraph().setStyle("H1");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            List<RuleDraft> rules = parser.parse(out.toByteArray());
            RuleDraft font = find(rules, "FONT", "HEADING_1");
            assertEquals("Arial", mapper.readTree(font.ruleConfigJson())
                    .path("expect").path("family").asText());
            RuleDraft size = find(rules, "SIZE", "HEADING_1");
            assertEquals(12.0, mapper.readTree(size.ruleConfigJson())
                    .path("expect").path("pt").asDouble(), 0.01);
        }
    }

    /** 真实语料（C 语言模板冒烟）：w:eastAsia 字体回退链「黑体;SimHei」须拆主名+别名。 */
    @Test
    void fontFallbackChainSplitsPrimaryAndAliases() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFStyles styles = doc.createStyles();
            CTStyle h1 = CTStyle.Factory.newInstance();
            h1.setStyleId("H1");
            h1.setType(STStyleType.PARAGRAPH);
            h1.addNewName().setVal("heading 1");
            h1.addNewRPr().addNewRFonts().setEastAsia("黑体;SimHei;黑体-简");
            styles.addStyle(new XWPFStyle(h1));
            doc.createParagraph().setStyle("H1");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            var expect = mapper.readTree(find(parser.parse(out.toByteArray()), "FONT", "HEADING_1")
                    .ruleConfigJson());
            org.junit.jupiter.api.Assertions.assertEquals("黑体",
                    expect.path("expect").path("eastAsia").asText());
            var aliases = new java.util.ArrayList<String>();
            expect.path("tolerance").path("aliases").forEach(n -> aliases.add(n.asText()));
            org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("SimHei", "黑体-简"), aliases);
        }
    }

    /** 真实语料：lineRule 固定但无 w:line 值=无具体约束，不得产出缺 pt 的规则（保存闸会拒）。 */
    @Test
    void fixedLineRuleWithoutLineValueProducesNoSpacingRule() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFStyles styles = doc.createStyles();
            CTStyle normal = CTStyle.Factory.newInstance();
            normal.setStyleId("N");
            normal.setType(STStyleType.PARAGRAPH);
            normal.addNewName().setVal("Normal");
            CTSpacing spacing = normal.addNewPPr().addNewSpacing();
            spacing.setLineRule(STLineSpacingRule.AT_LEAST); // 无 setLine
            styles.addStyle(new XWPFStyle(normal));
            doc.createParagraph();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            assertTrue(parser.parse(out.toByteArray()).stream()
                    .noneMatch(r -> r.ruleType().equals("LINE_SPACING")));
        }
    }

    /** 第四轮 P2-1：模板只声明中文槽时，规则不得把中文槽复制填充成 ASCII 期望。 */
    @Test
    void eastAsiaOnlyStyleKeepsSlotsSeparate() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFStyles styles = doc.createStyles();
            CTStyle h1 = CTStyle.Factory.newInstance();
            h1.setStyleId("H1");
            h1.setType(STStyleType.PARAGRAPH);
            h1.addNewName().setVal("heading 1");
            h1.addNewRPr().addNewRFonts().setEastAsia("黑体");
            styles.addStyle(new XWPFStyle(h1));
            doc.createParagraph().setStyle("H1");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            List<RuleDraft> rules = parser.parse(out.toByteArray());
            RuleDraft font = rules.stream().filter(r -> r.ruleType().equals("FONT")
                    && r.targetSelector().equals("HEADING_1")).findFirst().orElseThrow();
            var expect = mapper.readTree(font.ruleConfigJson()).path("expect");
            assertFalse(expect.has("family"), "East Asia-only 不得伪造 ASCII 槽期望");
            org.junit.jupiter.api.Assertions.assertEquals("黑体", expect.path("eastAsia").asText());
        }
    }

    /** 终审 Minor（第四轮升级语义）：EXACT 固定行距产出 lineRule+pt 规则，绝不产出/误读为倍数。 */
    @Test
    void exactLineRuleStyleProducesNoSpacingRule() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFStyles styles = doc.createStyles();
            CTStyle ct = CTStyle.Factory.newInstance();
            ct.setStyleId("H1");
            ct.setType(STStyleType.PARAGRAPH);
            ct.addNewName().setVal("heading 1");
            CTSpacing spacing = ct.addNewPPr().addNewSpacing();
            spacing.setLineRule(STLineSpacingRule.EXACT);
            spacing.setLine(BigInteger.valueOf(480));
            styles.addStyle(new XWPFStyle(ct));
            var p = doc.createParagraph();
            p.setStyle("H1");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            List<RuleDraft> rules = parser.parse(out.toByteArray());
            RuleDraft line = rules.stream().filter(r -> r.ruleType().equals("LINE_SPACING"))
                    .findFirst().orElseThrow(() -> new AssertionError("EXACT 样式应产出固定行距规则：" + rules));
            var expect = mapper.readTree(line.ruleConfigJson()).path("expect");
            org.junit.jupiter.api.Assertions.assertEquals("exact", expect.path("lineRule").asText());
            org.junit.jupiter.api.Assertions.assertEquals(24.0, expect.path("pt").asDouble(), 0.01);
            assertFalse(expect.has("multiplier"), "固定磅值不得伪装倍数期望：" + expect);
        }
    }

    @Test
    void rejectsNonDocxBytes() {
        assertThrows(TemplateParseException.class, () -> parser.parse("not a docx".getBytes()));
    }

    /** D-15 错题集发现：PDF 转换件页边距可为 0——解析端与保存闸同值域，0/越界键不产出。 */
    @Test
    void marginKeysOutsideSaveGateDomainAreNotEmitted() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            CTPageMar mar = doc.getDocument().getBody().addNewSectPr().addNewPgMar();
            mar.setTop(BigInteger.ZERO); // (0,100] 之外 → 不产
            mar.setLeft(BigInteger.valueOf(1440)); // 25.4mm → 产
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            var expect = mapper.readTree(find(parser.parse(out.toByteArray()), "MARGIN", "PAGE")
                    .ruleConfigJson()).path("expect");
            assertFalse(expect.has("top"), "0 值边距键不得产出：" + expect);
            assertEquals(25.4, expect.path("left").asDouble(), 0.01);
            // 且必须能过保存闸（两处契约同源）
            RuleConfig.parse("MARGIN", find(parser.parse(out.toByteArray()), "MARGIN", "PAGE")
                    .ruleConfigJson(), mapper);
        }
    }
}
