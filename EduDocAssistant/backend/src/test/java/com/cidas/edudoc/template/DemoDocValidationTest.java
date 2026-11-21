package com.cidas.edudoc.template;

import com.cidas.edudoc.document.domain.ChunkingResult;
import com.cidas.edudoc.document.domain.ChunkingService;
import com.cidas.edudoc.document.domain.TextBlock;
import com.cidas.edudoc.document.infrastructure.TikaTextExtractor;
import com.cidas.edudoc.template.domain.FormatRuleMatcher;
import com.cidas.edudoc.template.domain.RuleDraft;
import com.cidas.edudoc.template.domain.StyleSnapshot;
import com.cidas.edudoc.template.infrastructure.DocxStyleExtractor;
import com.cidas.edudoc.template.infrastructure.FormatRule;
import com.cidas.edudoc.template.infrastructure.TemplateOutlineGenerator;
import com.cidas.edudoc.template.infrastructure.TemplateRuleParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 演示文档回归：模板能解析出规则，合规报告应零问题，格式问题示例应有问题。 */
class DemoDocValidationTest {

    private static final Path DIR = Path.of("..", "docs", "demo");
    private final TemplateRuleParser parser = new TemplateRuleParser(new ObjectMapper());
    private final DocxStyleExtractor extractor = new DocxStyleExtractor();
    private final FormatRuleMatcher matcher = new FormatRuleMatcher(new ObjectMapper());

    private List<FormatRule> rules() throws Exception {
        byte[] bytes = Files.readAllBytes(DIR.resolve("实验五_泛型类和泛型接口_模板.docx"));
        List<RuleDraft> drafts = parser.parse(bytes);
        return java.util.stream.IntStream.range(0, drafts.size())
                .mapToObj(i -> {
                    RuleDraft d = drafts.get(i);
                    return new FormatRule("R" + i, "V1", d.ruleType(), d.targetSelector(),
                            d.severity(), d.autoFixable(), d.ruleConfigJson(), d.ruleOrder());
                })
                .toList();
    }

    @Test
    void templateRulesParseAndCompliantReportPasses() throws Exception {
        List<FormatRule> rules = rules();
        assertFalse(rules.isEmpty());
        assertTrue(rules.stream().anyMatch(r -> "MARGIN".equals(r.getRuleType())));
        assertTrue(rules.stream().anyMatch(r -> "HEADER_FOOTER".equals(r.getRuleType())));
        assertTrue(rules.stream().anyMatch(r -> "FONT".equals(r.getRuleType())
                && "BODY".equals(r.getTargetSelector())));
        assertTrue(rules.stream().anyMatch(r -> "SIZE".equals(r.getRuleType())
                && "HEADING_1".equals(r.getTargetSelector())));
        StyleSnapshot snapshot = extractor.extract(Files.readAllBytes(
                DIR.resolve("实验五_泛型类和泛型接口_学生报告.docx")));
        assertTrue(matcher.match(rules, snapshot).isEmpty(),
                "合规报告不应有格式问题：" + matcher.match(rules, snapshot));
        // 「按模板新建」实际生成的骨架也必须零问题
        byte[] generated = new TemplateOutlineGenerator(new ObjectMapper())
                .generate("实验五 泛型类和泛型接口", rules);
        StyleSnapshot generatedSnap = extractor.extract(generated);
        assertTrue(matcher.match(rules, generatedSnap).isEmpty(),
                "按模板新建的骨架不应有格式问题：" + matcher.match(rules, generatedSnap));
        StyleSnapshot skeletonSnap = extractor.extract(Files.readAllBytes(
                DIR.resolve("实验五_泛型类和泛型接口_按模板新建示例.docx")));
        assertTrue(matcher.match(rules, skeletonSnap).isEmpty(),
                "骨架参考文档不应有格式问题：" + matcher.match(rules, skeletonSnap));
    }

    @Test
    void reportUploadsAndParsesWithStructure() throws Exception {
        Path report = DIR.resolve("实验五_泛型类和泛型接口_学生报告.docx");
        List<TextBlock> blocks = new TikaTextExtractor().extract(report, report.getFileName().toString());
        assertFalse(blocks.isEmpty(), "上传解析不应为空");
        ChunkingResult chunked = new ChunkingService(1500).chunk(blocks);
        assertTrue(chunked.nodes().stream().anyMatch(n -> TextBlock.HEADING.equals(n.kind())
                        && n.start() == null && n.text() != null && !n.text().isBlank()),
                "上传报告应能生成标记型标题结构：" + chunked.nodes());
        assertTrue(blocks.stream().anyMatch(b -> b.text().contains("Point<T extends Number>")),
                "上传报告应包含实验正文");
    }

    @Test
    void issueSampleReportsProblems() throws Exception {
        StyleSnapshot snapshot = extractor.extract(Files.readAllBytes(
                DIR.resolve("实验五_泛型类和泛型接口_格式问题示例.docx")));
        assertFalse(matcher.match(rules(), snapshot).isEmpty(), "格式问题示例应产生问题");
    }
}
