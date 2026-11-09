package com.cidas.edudoc.template.domain;

import com.cidas.edudoc.template.domain.StyleSnapshot.LineSpacing;
import com.cidas.edudoc.template.domain.StyleSnapshot.PageFacts;
import com.cidas.edudoc.template.domain.StyleSnapshot.ParagraphFact;
import com.cidas.edudoc.template.domain.StyleSnapshot.RunFact;
import com.cidas.edudoc.template.infrastructure.FormatRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-04 规则比对引擎（§3.3.2 伪代码）：UT-11 字号不符出 BLOCKER+期望值、容差、题注/页眉/参考文献存在性。 */
class FormatRuleMatcherTest {

    private final FormatRuleMatcher matcher = new FormatRuleMatcher(new com.fasterxml.jackson.databind.ObjectMapper());

    private static FormatRule rule(String type, String selector, String severity, String fixable,
                                   String configJson, int order) {
        return new FormatRule("R-" + type + selector, "TV1", type, selector, severity, fixable, configJson, order);
    }

    private static StyleSnapshot snapshot() {
        List<ParagraphFact> paragraphs = List.of(
                new ParagraphFact(0, "HEADING_1", "黑体", null, 16.0, new LineSpacing("AUTO", 1.5), "绪论"),
                new ParagraphFact(1, "HEADING_1", "宋体", null, 14.0, new LineSpacing("AUTO", 1.0), "设计"),
                new ParagraphFact(2, "BODY", "宋体", null, 12.0, new LineSpacing("AUTO", 1.0), "正文一段"),
                new ParagraphFact(3, "FIGURE_CAPTION", null, null, null, null, "图 2-1 架构图"),
                new ParagraphFact(4, "TABLE_CAPTION", null, null, null, null, "表3 清单"));
        PageFacts page = new PageFacts(25.4, 25.4, 30.0, 25.4, 210.0, 297.0, false, true);
        return new StyleSnapshot(paragraphs, page);
    }

    private List<IssueDraft> runAll() {
        List<FormatRule> rules = List.of(
                rule("FONT", "HEADING_1", "BLOCKER", "Y",
                        "{\"expect\":{\"family\":\"黑体\"},\"fix\":{\"family\":\"黑体\"}}", 10),
                rule("SIZE", "HEADING_1", "BLOCKER", "Y",
                        "{\"expect\":{\"pt\":16},\"tolerance\":{\"pt\":0.5},\"fix\":{\"pt\":16}}", 11),
                rule("LINE_SPACING", "HEADING_1", "BLOCKER", "Y",
                        "{\"expect\":{\"multiplier\":1.5},\"fix\":{\"multiplier\":1.5}}", 12),
                rule("SIZE", "BODY", "WARNING", "Y",
                        "{\"expect\":{\"pt\":12},\"tolerance\":{\"pt\":0.5},\"fix\":{\"pt\":12}}", 11),
                rule("MARGIN", "PAGE", "BLOCKER", "Y",
                        "{\"expect\":{\"top\":25.4,\"bottom\":25.4,\"left\":31.75,\"right\":25.4,\"unit\":\"mm\"},"
                                + "\"fix\":{\"top\":25.4,\"bottom\":25.4,\"left\":31.75,\"right\":25.4}}", 13),
                rule("HEADER_FOOTER", "HEADER", "WARNING", "N",
                        "{\"expect\":{\"present\":true}}", 14),
                rule("CAPTION", "FIGURE_CAPTION", "WARNING", "N",
                        "{\"expect\":{\"perChapter\":true}}", 20),
                rule("CAPTION", "TABLE_CAPTION", "WARNING", "N",
                        "{\"expect\":{\"perChapter\":true}}", 20),
                rule("REFERENCE", "REFERENCE", "WARNING", "N",
                        "{\"expect\":{\"standard\":\"GB/T 7714\"}}", 40));
        return matcher.match(rules, snapshot());
    }

    private IssueDraft find(List<IssueDraft> issues, String type, String actualContains) {
        return issues.stream()
                .filter(i -> i.issueType().equals(type)
                        && String.valueOf(i.actualValue()).contains(actualContains))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少问题 " + type + "/" + actualContains + "，实际=" + issues));
    }

    /** 第五轮 P2-5：混排 run 逐一比对——首 run 符合不得掩盖后续 run 的错误。 */
    @Test
    void mixedRunsAreComparedIndividually() {
        StyleSnapshot snapshot = new StyleSnapshot(List.of(
                new StyleSnapshot.ParagraphFact(0, "BODY", "Arial", null, 12.0,
                        new LineSpacing("AUTO", 1.0), "AB", List.of(
                        new StyleSnapshot.RunFact(0, 1, "A", "Arial", null, 12.0),
                        new StyleSnapshot.RunFact(1, 2, "B", "宋体", null, 12.0)))),
                new PageFacts(25.4, 25.4, 31.75, 25.4, 210.0, 297.0, true, true));
        List<FormatRule> rules = List.of(rule("FONT", "BODY", "WARNING", "Y",
                "{\"expect\":{\"family\":\"Arial\"},\"fix\":{\"family\":\"Arial\"}}", 10));

        List<IssueDraft> issues = matcher.match(rules, snapshot);

        assertEquals(1, issues.size());
        assertEquals("宋体", issues.get(0).actualValue());
        assertTrue(issues.get(0).locationRef().contains("\"paragraphIndex\":0"));
        assertTrue(issues.get(0).locationRef().contains("\"charStart\":1"));
    }

    /** 第五轮 P2-1（fail-closed）：存量脏配置不再静默跳过，产出「规则配置非法」问题。 */
    @Test
    void legacyInvalidConfigFailsClosed() {
        StyleSnapshot snapshot = new StyleSnapshot(List.of(
                new ParagraphFact(0, "BODY", "宋体", null, 12.0, new LineSpacing("AUTO", 1.0), "正文")),
                new PageFacts(25.4, 25.4, 31.75, 25.4, 210.0, 297.0, true, true));
        List<IssueDraft> issues = matcher.match(
                List.of(rule("SIZE", "BODY", "WARNING", "Y", "{\"expect\":{}}", 11)), snapshot);
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).actualValue().contains("规则配置非法"));
    }

    /** 第四轮 P1-2：文档实际固定行距 vs 规则期望倍数 = 不符（不得以 null 跳过判通过）。 */
    @Test
    void fixedActualLineViolatesMultiplierExpectation() {
        StyleSnapshot snapshot = new StyleSnapshot(List.of(
                new ParagraphFact(0, "HEADING_1", "黑体", null, 16.0,
                        new LineSpacing("EXACT", 24.0), "绪论")),
                new PageFacts(25.4, 25.4, 31.75, 25.4, 210.0, 297.0, true, true));
        List<FormatRule> rules = List.of(rule("LINE_SPACING", "HEADING_1", "BLOCKER", "Y",
                "{\"expect\":{\"multiplier\":1.5},\"fix\":{\"multiplier\":1.5}}", 12));
        List<IssueDraft> issues = matcher.match(rules, snapshot);
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).actualValue().contains("固定"));
        assertTrue(issues.get(0).expectedValue().contains("1.5"));
    }

    /** 第四轮 P1-2：期望固定磅值本身可比对，相同磅值通过、不同不符。 */
    @Test
    void fixedExpectationCompares() {
        StyleSnapshot snapshot = new StyleSnapshot(List.of(
                new ParagraphFact(0, "HEADING_1", "黑体", null, 16.0,
                        new LineSpacing("EXACT", 20.0), "绪论")),
                new PageFacts(25.4, 25.4, 31.75, 25.4, 210.0, 297.0, true, true));
        List<FormatRule> pass = List.of(rule("LINE_SPACING", "HEADING_1", "BLOCKER", "Y",
                "{\"expect\":{\"lineRule\":\"exact\",\"pt\":20}}", 12));
        assertEquals(List.of(), matcher.match(pass, snapshot));
        List<FormatRule> fail = List.of(rule("LINE_SPACING", "HEADING_1", "BLOCKER", "Y",
                "{\"expect\":{\"lineRule\":\"exact\",\"pt\":24}}", 12));
        assertEquals(1, matcher.match(fail, snapshot).size());
    }

    /** 第四轮 P2-2：ASCII 槽与字号实际缺失 = 「未声明」不符，与中文槽/页边距统一缺失值语义。 */
    @Test
    void missingAsciiAndSizeAreUndeclaredViolations() {
        StyleSnapshot snapshot = new StyleSnapshot(List.of(
                new ParagraphFact(0, "BODY", null, null, null, new LineSpacing("AUTO", 1.0), "正文段")),
                new PageFacts(25.4, 25.4, 31.75, 25.4, 210.0, 297.0, true, true));
        List<FormatRule> rules = List.of(
                rule("FONT", "BODY", "WARNING", "Y",
                        "{\"expect\":{\"family\":\"宋体\"},\"fix\":{\"family\":\"宋体\"}}", 10),
                rule("SIZE", "BODY", "WARNING", "Y",
                        "{\"expect\":{\"pt\":12},\"tolerance\":{\"pt\":0.5},\"fix\":{\"pt\":12}}", 11));
        List<IssueDraft> issues = matcher.match(rules, snapshot);
        assertEquals(2, issues.size());
        assertTrue(issues.stream().anyMatch(i -> i.actualValue().contains("西文槽未声明")));
        assertTrue(issues.stream().anyMatch(i -> i.actualValue().contains("字号未声明")));
    }

    /** 1108 复审 P2-1 残余：CODE keepIndent 真正消费——缩进丢失逐段报，保留缩进不报。 */
    @Test
    void codeKeepIndentViolationsReported() {
        ParagraphFact noIndent = new ParagraphFact(7, "CODE_BLOCK", "Consolas", null,
                10.5, new LineSpacing("AUTO", 1.0), "int main() {}",
                List.of(new RunFact(0, 13, "int main() {}", null, null, null)));
        ParagraphFact indented = new ParagraphFact(9, "CODE_BLOCK", "Consolas", null,
                10.5, new LineSpacing("AUTO", 1.0), "int main() {}",
                List.of(new RunFact(0, 17, "    int main() {}", null, null, null)));
        StyleSnapshot page = new StyleSnapshot(List.of(noIndent, indented),
                new PageFacts(25.4, 25.4, 31.75, 25.4, 210.0, 297.0, true, true));
        List<FormatRule> rules = List.of(rule("CODE", "CODE_BLOCK", "WARNING", "N",
                "{\"expect\":{\"style\":\"Code\",\"keepIndent\":true}}", 30));

        var issues = matcher.match(rules, page);

        assertEquals(1, issues.size());
        assertEquals("int main() {}", issues.get(0).actualValue());
        assertTrue(issues.get(0).locationRef().contains("\"paragraphIndex\":7"));
    }

    /** 1107 P1-2：字体回退别名（根级 tolerance.aliases）命中即通过。 */
    @Test
    void fontAliasSatisfiesExpectation() {
        StyleSnapshot snapshot = new StyleSnapshot(List.of(
                new ParagraphFact(0, "HEADING_1", "SimHei", null, 16.0,
                        new LineSpacing("AUTO", 1.5), "绪论")),
                new PageFacts(25.4, 25.4, 31.75, 25.4, 210.0, 297.0, true, true));
        List<FormatRule> rules = List.of(rule("FONT", "HEADING_1", "BLOCKER", "Y",
                "{\"expect\":{\"family\":\"黑体\"},\"tolerance\":{\"aliases\":[\"SimHei\"]},"
                        + "\"fix\":{\"family\":\"黑体\"}}", 10));
        assertEquals(List.of(), matcher.match(rules, snapshot));
    }

    /** 1107 P1-2：根级自定义容差必须生效（0.2 时 12.4 vs 12 报问题；误读默认 0.5 则漏报）。 */
    @Test
    void rootTolerancePtIsConsumed() {
        StyleSnapshot snapshot = new StyleSnapshot(List.of(
                new ParagraphFact(0, "BODY", null, null, 12.4, new LineSpacing("AUTO", 1.0), "正文")),
                new PageFacts(25.4, 25.4, 31.75, 25.4, 210.0, 297.0, true, true));
        List<FormatRule> rules = List.of(rule("SIZE", "BODY", "WARNING", "Y",
                "{\"expect\":{\"pt\":12},\"tolerance\":{\"pt\":0.2},\"fix\":{\"pt\":12}}", 11));
        assertEquals(1, matcher.match(rules, snapshot).size());
    }

    /** 第三轮 P2-1：文档未声明 pgMar 时页边距不得静默通过（与缺 pgSz 同类缺陷补齐）。 */
    @Test
    void missingMarginsAreUndeclaredViolations() {
        StyleSnapshot snapshot = new StyleSnapshot(List.of(),
                new StyleSnapshot.PageFacts(null, null, null, null, 210.0, 297.0, true, true));
        List<FormatRule> rules = List.of(rule("MARGIN", "PAGE", "BLOCKER", "Y",
                "{\"expect\":{\"top\":25.4,\"bottom\":25.4,\"left\":31.75,\"right\":25.4}}", 13));
        List<IssueDraft> issues = matcher.match(rules, snapshot);
        assertEquals(1, issues.size());
        assertEquals("BLOCKER", issues.get(0).severity());
        assertTrue(issues.get(0).actualValue().contains("未声明"));
    }

    /** 第三轮 P2-2：期望中文字体槽而段落未声明 East Asia → 「槽未声明」问题，不得静默通过。 */
    @Test
    void missingEastAsiaSlotIsUndeclaredViolation() {
        StyleSnapshot snapshot = new StyleSnapshot(List.of(
                new StyleSnapshot.ParagraphFact(0, "HEADING_1", "Arial", null, 16.0, new LineSpacing("AUTO", 1.5), "标题")),
                new StyleSnapshot.PageFacts(25.4, 25.4, 31.75, 25.4, 210.0, 297.0, true, true));
        List<FormatRule> rules = List.of(rule("FONT", "HEADING_1", "BLOCKER", "Y",
                "{\"expect\":{\"eastAsia\":\"黑体\"},\"fix\":{\"eastAsia\":\"黑体\"}}", 10));
        List<IssueDraft> issues = matcher.match(rules, snapshot);
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).actualValue().contains("未声明"));
    }

    /** 复审 P1-1：East Asia 中文字体槽独立比对，ASCII 相同不得掩盖中文槽不一致。 */
    @Test
    void eastAsiaSlotViolatesIndependently() {
        StyleSnapshot snapshot = new StyleSnapshot(List.of(
                new StyleSnapshot.ParagraphFact(0, "HEADING_1", "Times New Roman", "黑体",
                        16.0, new LineSpacing("AUTO", 1.5), "绪论"),
                new StyleSnapshot.ParagraphFact(1, "HEADING_1", "Times New Roman", "宋体",
                        16.0, new LineSpacing("AUTO", 1.5), "设计")),
                new StyleSnapshot.PageFacts(25.4, 25.4, 31.75, 25.4, 210.0, 297.0, true, true));
        List<FormatRule> rules = List.of(rule("FONT", "HEADING_1", "BLOCKER", "Y",
                "{\"expect\":{\"family\":\"Times New Roman\",\"eastAsia\":\"黑体\"}}", 10));

        List<IssueDraft> issues = matcher.match(rules, snapshot);

        assertEquals(1, issues.size());
        assertEquals("宋体", issues.get(0).actualValue());
        assertTrue(issues.get(0).expectedValue().contains("黑体"));
    }

    /** 复审 P1-2：仅段前/段后间距不得截断样式行距继承（误报回归）。 */
    @Test
    void blankTextParagraphsAreNotCompared() {
        StyleSnapshot snapshot = new StyleSnapshot(List.of(
                new StyleSnapshot.ParagraphFact(0, "BODY", "宋体", null, 12.0, new LineSpacing("AUTO", 1.0), "   ")),
                new StyleSnapshot.PageFacts(25.4, 25.4, 31.75, 25.4, 210.0, 297.0, true, true));
        List<FormatRule> rules = List.of(rule("SIZE", "BODY", "WARNING", "Y",
                "{\"expect\":{\"pt\":10.5},\"tolerance\":{\"pt\":0.5}}", 11));
        assertEquals(List.of(), matcher.match(rules, snapshot));
    }

    /** 复审 P2-1：期望 A4 而文档未声明 pgSz → BLOCKER「无法确认纸型」，不得静默通过。 */
    @Test
    void missingPageSizeProducesBlockingIssue() {
        StyleSnapshot snapshot = new StyleSnapshot(List.of(),
                new StyleSnapshot.PageFacts(25.4, 25.4, 31.75, 25.4, null, null, true, true));
        List<FormatRule> rules = List.of(rule("MARGIN", "PAGE", "BLOCKER", "Y",
                "{\"expect\":{\"top\":25.4,\"bottom\":25.4,\"left\":31.75,\"right\":25.4,"
                        + "\"width\":210,\"height\":297}}", 13));
        List<IssueDraft> issues = matcher.match(rules, snapshot);
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).actualValue().contains("未声明"));
    }

    @Test
    void headingViolationsCarryExpectedActualAndLocation() {
        List<IssueDraft> issues = runAll();

        IssueDraft font = find(issues, "FONT", "宋体");
        assertEquals("BLOCKER", font.severity());
        assertEquals("黑体", font.expectedValue());
        assertEquals("PARAGRAPH", font.locationType());
        assertTrue(font.locationRef().contains("\"paragraphIndex\":1"));
        assertEquals("Y", font.autoFixable());

        IssueDraft size = find(issues, "SIZE", "14");
        assertEquals("16.0pt", size.expectedValue());
        assertEquals("14.0pt", size.actualValue());

        find(issues, "LINE_SPACING", "1.0");
    }

    @Test
    void bodySizeWithinTolerancePasses() {
        // BODY 12pt 与期望 12±0.5 一致：不应有 BODY 段落（index 2）的 SIZE 问题
        List<IssueDraft> issues = runAll().stream()
                .filter(i -> i.issueType().equals("SIZE") && i.locationRef().contains("\"paragraphIndex\":2"))
                .toList();
        assertEquals(List.of(), issues);
    }

    @Test
    void marginAndHeaderAndExistenceRules() {
        List<IssueDraft> issues = runAll();

        IssueDraft margin = find(issues, "MARGIN", "left=30");
        assertEquals("BLOCKER", margin.severity());
        assertTrue(margin.expectedValue().contains("31.75"));
        assertEquals("PAGE", margin.locationType());

        find(issues, "HEADER_FOOTER", "缺失");
        find(issues, "REFERENCE", "缺失");
    }

    /** 1106 审查 P2-2：A4 期望（210×297±3mm），Letter 纸型必须被检出。 */
    @Test
    void letterPageViolatesA4Expectation() {
        StyleSnapshot letter = new StyleSnapshot(List.of(),
                new StyleSnapshot.PageFacts(25.4, 25.4, 31.75, 25.4, 215.9, 279.4, true, true));
        List<FormatRule> rules = List.of(rule("MARGIN", "PAGE", "BLOCKER", "Y",
                "{\"expect\":{\"top\":25.4,\"bottom\":25.4,\"left\":31.75,\"right\":25.4,"
                        + "\"width\":210,\"height\":297,\"unit\":\"mm\"}}", 13));
        List<IssueDraft> issues = matcher.match(rules, letter);
        assertEquals(1, issues.size());
        assertEquals("PAGE", issues.get(0).locationType());
        assertTrue(issues.get(0).expectedValue().contains("width=210"));
        assertTrue(issues.get(0).actualValue().contains("width=215.9"));
    }

    /** 1106 审查 P2-4：perChapter=false 的模板按保存的 pattern 实际校验，不再空操作。 */
    @Test
    void captionPatternCheckRunsForNonChapterNumbering() {
        StyleSnapshot snapshot = new StyleSnapshot(List.of(
                new StyleSnapshot.ParagraphFact(0, "TABLE_CAPTION", null, null, null, null, "表2-1 接口清单")),
                new StyleSnapshot.PageFacts(25.4, 25.4, 31.75, 25.4, 210.0, 297.0, true, true));
        List<FormatRule> rules = List.of(rule("CAPTION", "TABLE_CAPTION", "WARNING", "N",
                "{\"expect\":{\"perChapter\":false,\"pattern\":\"^表\\\\s*\\\\d+\\\\s\"}}", 20));
        List<IssueDraft> issues = matcher.match(rules, snapshot);
        assertEquals(1, issues.size());
        assertEquals("表2-1 接口清单", issues.get(0).actualValue());
    }

    @Test
    void captionPatternChecksPerChapter() {
        List<IssueDraft> issues = runAll();

        // 图 2-1 合规无问题；表3 缺章号 → 一条 TABLE_CAPTION 问题
        long figureIssues = issues.stream()
                .filter(i -> i.issueType().equals("CAPTION") && i.locationRef().contains("\"paragraphIndex\":3"))
                .count();
        assertEquals(0, figureIssues);
        IssueDraft table = find(issues, "CAPTION", "表3 清单");
        assertEquals("WARNING", table.severity());
        assertEquals("N", table.autoFixable());
    }
}
