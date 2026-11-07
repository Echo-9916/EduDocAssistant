package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.template.domain.StyleSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-07 生成↔提取真往返：大纲 docx 的标题样式/页面设置必须满足模板规则（按模板新建即合规起点）。 */
class TemplateOutlineGeneratorTest {

    private final TemplateOutlineGenerator generator =
            new TemplateOutlineGenerator(new com.fasterxml.jackson.databind.ObjectMapper());
    private final DocxStyleExtractor extractor = new DocxStyleExtractor();

    private static FormatRule rule(String type, String selector, String config) {
        return new FormatRule("R1", "V1", type, selector, "BLOCKER", "Y", config, 10);
    }

    /** 第四轮 P1-2/D-07：固定行距规则必须被保真生成（EXACT 20 磅），不得回退成 1.5 倍。 */
    @Test
    void fixedLineRuleRoundTrips() {
        byte[] docx = generator.generate("报告", List.of(
                rule("LINE_SPACING", "HEADING_1",
                        "{\"expect\":{\"lineRule\":\"exact\",\"pt\":20}}")));

        StyleSnapshot.ParagraphFact heading = extractor.extract(docx).withSelector("HEADING_1").get(0);
        assertEquals("EXACT", heading.line().kind());
        assertEquals(20.0, heading.line().value(), 0.01);
    }

    /** 第三轮 P1-1：规则双字体槽必须分别写入生成文档的 ascii/eastAsia——生成后回读验证。 */
    @Test
    void generatedOutlineKeepsSeparateFontSlots() {
        List<FormatRule> rules = List.of(
                rule("FONT", "HEADING_1",
                        "{\"expect\":{\"family\":\"Times New Roman\",\"eastAsia\":\"黑体\"},"
                                + "\"fix\":{\"family\":\"Times New Roman\",\"eastAsia\":\"黑体\"}}"));

        StyleSnapshot snapshot = extractor.extract(generator.generate("报告", rules));

        StyleSnapshot.ParagraphFact heading = snapshot.withSelector("HEADING_1").get(0);
        assertEquals("Times New Roman", heading.family());
        assertEquals("黑体", heading.familyEastAsia());
    }

    @Test
    void outlineSatisfiesTemplateRules() {
        List<FormatRule> rules = List.of(
                rule("FONT", "HEADING_1", "{\"expect\":{\"family\":\"黑体\"}}"),
                rule("SIZE", "HEADING_1", "{\"expect\":{\"pt\":16}}"),
                rule("LINE_SPACING", "HEADING_1", "{\"expect\":{\"multiplier\":1.5}}"),
                rule("MARGIN", "PAGE",
                        "{\"expect\":{\"top\":25.4,\"bottom\":25.4,\"left\":31.75,\"right\":25.4,\"unit\":\"mm\"}}"),
                rule("HEADER_FOOTER", "HEADER", "{\"expect\":{\"present\":true}}"));

        byte[] docx = generator.generate("课程设计报告", rules);
        StyleSnapshot snapshot = extractor.extract(docx);

        StyleSnapshot.ParagraphFact heading = snapshot.withSelector("HEADING_1").get(0);
        assertEquals("黑体", heading.family());
        assertEquals(16.0, heading.pt(), 0.01);
        assertEquals(1.5, heading.line().value(), 0.01);
        assertEquals(25.4, snapshot.page().top(), 0.1);
        assertEquals(31.75, snapshot.page().left(), 0.1);
        assertTrue(snapshot.page().headerPresent());
        // 1106 审查 P2-2：生成器必须显式写入 A4 纸型
        assertEquals(210.0, snapshot.page().widthMm(), 1.0);
        assertEquals(297.0, snapshot.page().heightMm(), 1.0);
        // 大纲含一级标题骨架（绪论/设计/结论）与正文占位
        assertTrue(snapshot.paragraphs().stream()
                .anyMatch(p -> "BODY".equals(p.selector()) && p.text().contains("课程设计报告")));
    }
}
