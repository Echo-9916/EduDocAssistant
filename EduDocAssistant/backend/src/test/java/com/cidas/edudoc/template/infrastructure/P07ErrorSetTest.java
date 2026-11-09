package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.template.domain.FormatRuleMatcher;
import com.cidas.edudoc.template.domain.IssueDraft;
import com.cidas.edudoc.template.domain.RuleConfig;
import com.cidas.edudoc.template.domain.RuleDraft;
import com.cidas.edudoc.template.domain.StyleSnapshot;
import com.cidas.edudoc.template.domain.StyleSnapshot.ParagraphFact;
import com.cidas.edudoc.template.domain.StyleSnapshot.RunFact;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-15 / P-07 错题集：以真实课程模板 .docx 为底本，POI 注入已知问题
 * （字号/字体/行距/页边距/题注各若干处）→ 产出 mutated docx + ground truth JSON
 * （`docs/test/fixtures/mutated/`，幂等可重跑）→ 走与生产同一比对链
 * （TemplateRuleParser→RuleConfig→DocxStyleExtractor→FormatRuleMatcher）统计漏报率，
 * 验收口径：命中/应命中 ≥ 95%。
 *
 * 题注出题说明：提取器题注 token 字符集是规则 pattern 的真子集，纯文本破坏会同时脱离
 * selector；故 CAPTION 仅注到「题注样式」段落（样式保分类、文本换全角冒号破 pattern），
 * 无题注样式段落则记 skipped，不硬造。
 */
class P07ErrorSetTest {

    private static final List<String> SOURCES = List.of(
            "Web开发实战课程设计报告撰写要求.docx",
            "C语言课程设计报告格式.docx",
            "Oracle应用项目报告模版2026.docx",
            "JavaEE课程作业模板.docx");
    private static final int CAP_PER_RULE = 2;
    private static final List<String> EAST_DECOYS = List.of("隶书", "幼圆", "仿宋");
    private static final List<String> LATIN_DECOYS = List.of("Impact", "Chiller", "Bookman Old Style");

    private final ObjectMapper mapper = new ObjectMapper();
    private final TemplateRuleParser parser = new TemplateRuleParser(mapper);
    private final DocxStyleExtractor extractor = new DocxStyleExtractor();
    private final FormatRuleMatcher matcher = new FormatRuleMatcher(mapper);

    private record Injection(String ruleId, String ruleType, Integer paragraphIndex, String note) {
    }

    @Test
    void generateErrorSetAndScoreP07() throws Exception {
        Path repo = repoDir();
        Path templatesDir = repo.resolve("docs/test/fixtures/templates");
        Path outDir = repo.resolve("docs/test/fixtures/mutated");
        Files.createDirectories(outDir);

        int totalExpected = 0;
        int totalHits = 0;
        ArrayNode report = mapper.createArrayNode();

        for (String name : SOURCES) {
            Path source = templatesDir.resolve(name);
            if (!Files.exists(source)) {
                continue;
            }
            byte[] orig = Files.readAllBytes(source);
            List<RuleDraft> drafts = parser.parse(orig);
            StyleSnapshot base = extractor.extract(orig);
            List<FormatRule> rules = new ArrayList<>();
            for (int i = 0; i < drafts.size(); i++) {
                RuleDraft d = drafts.get(i);
                FormatRule r = new FormatRule();
                r.setId("R" + i);
                r.setTemplateVersionId("TV-P07");
                r.setRuleType(d.ruleType());
                r.setTargetSelector(d.targetSelector());
                r.setSeverity(d.severity());
                r.setAutoFixable(d.autoFixable());
                r.setRuleConfigJson(d.ruleConfigJson());
                r.setRuleOrder(d.ruleOrder());
                rules.add(r);
            }

            XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(orig));
            List<Injection> groundTruth = new ArrayList<>();
            ArrayNode skipped = mapper.createArrayNode();
            for (int i = 0; i < rules.size(); i++) {
                FormatRule rule = rules.get(i);
                RuleConfig config = RuleConfig.parse(rule.getRuleType(), rule.getRuleConfigJson(), mapper);
                switch (rule.getRuleType()) {
                    case "SIZE" -> injectSize(rule, config, base, doc, groundTruth);
                    case "FONT" -> injectFont(rule, config, base, doc, groundTruth, mapper);
                    case "LINE_SPACING" -> injectLine(rule, config, base, doc, groundTruth);
                    case "MARGIN" -> injectMargin(rule, doc, groundTruth, skipped);
                    case "CAPTION" -> injectCaption(rule, base, doc, groundTruth, skipped);
                    default -> {
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            doc.close();
            byte[] mutated = out.toByteArray();
            Path mutatedPath = outDir.resolve(name.replaceFirst("\\.docx$", "") + ".mutated.docx");
            Files.write(mutatedPath, mutated);

            List<IssueDraft> issues = matcher.match(rules, extractor.extract(mutated));
            int hits = 0;
            ArrayNode injected = mapper.createArrayNode();
            for (Injection g : groundTruth) {
                boolean hit = issues.stream().anyMatch(is -> is.ruleId().equals(g.ruleId())
                        && locationMatches(is, g.paragraphIndex()));
                if (hit) {
                    hits++;
                }
                ObjectNode row = injected.addObject();
                row.put("ruleId", g.ruleId());
                row.put("ruleType", g.ruleType());
                row.put("paragraphIndex", g.paragraphIndex() == null ? -1 : g.paragraphIndex());
                row.put("hit", hit);
                if (g.note() != null) {
                    row.put("note", g.note());
                }
            }
            ObjectNode node = report.addObject();
            node.put("source", name);
            node.put("mutated", "docs/test/fixtures/mutated/" + mutatedPath.getFileName());
            node.put("expected", groundTruth.size());
            node.put("hits", hits);
            node.set("injections", injected);
            node.set("skipped", skipped);
            totalExpected += groundTruth.size();
            totalHits += hits;
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("criterion", "P-07 漏报口径：命中/应命中 ≥ 95%");
        root.put("expectedTotal", totalExpected);
        root.put("hitsTotal", totalHits);
        if (totalExpected > 0) {
            root.put("recall", (double) totalHits / totalExpected);
        }
        root.set("templates", report);
        Files.writeString(outDir.resolve("groundtruth.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));

        System.out.println("P-07 错题集：应命中 " + totalExpected + "，实际命中 " + totalHits
                + "，recall=" + (totalExpected == 0 ? "N/A"
                : String.format("%.1f%%", 100.0 * totalHits / totalExpected)));
        assertTrue(totalExpected >= 12, "注入量不足，错题集无效: " + totalExpected);
        double recall = totalHits / (double) totalExpected;
        assertTrue(recall >= 0.95, "P-07 未达标 recall=" + recall + "（明细见 groundtruth.json）");
    }

    private static boolean locationMatches(IssueDraft issue, Integer paragraphIndex) {
        if (paragraphIndex == null) {
            return true; // 页面级（MARGIN）：规则命中即算
        }
        return issue.locationRef().contains("\"paragraphIndex\":" + paragraphIndex);
    }

    // ==================== 注入器 ====================

    private void injectSize(FormatRule rule, RuleConfig config, StyleSnapshot base,
                            XWPFDocument doc, List<Injection> gt) {
        Double expect = config.pt();
        if (expect == null) {
            return;
        }
        int done = 0;
        for (ParagraphFact p : base.withSelector(rule.getTargetSelector())) {
            if (done >= CAP_PER_RULE) {
                return;
            }
            RunFact run = firstNonBlank(p);
            if (run == null) {
                continue;
            }
            double actual = expect + 2.0;
            setRunSize(paragraphRun(doc, p.index(), run.charStart()), actual);
            gt.add(new Injection(rule.getId(), "SIZE", p.index(), formatPt(expect) + "→" + formatPt(actual)));
            done++;
        }
    }

    private void injectFont(FormatRule rule, RuleConfig config, StyleSnapshot base,
                            XWPFDocument doc, List<Injection> gt, ObjectMapper om) throws Exception {
        JsonNode expectNode = om.readTree(rule.getRuleConfigJson()).path("expect");
        String family = expectNode.path("family").isTextual() ? expectNode.path("family").asText() : null;
        String eastAsia = expectNode.path("eastAsia").isTextual() ? expectNode.path("eastAsia").asText() : null;
        if (family == null && eastAsia == null) {
            return;
        }
        int done = 0;
        for (ParagraphFact p : base.withSelector(rule.getTargetSelector())) {
            if (done >= CAP_PER_RULE) {
                return;
            }
            RunFact run = firstNonBlank(p);
            if (run == null) {
                continue;
            }
            XWPFRun target = paragraphRun(doc, p.index(), run.charStart());
            if (target == null) {
                continue;
            }
            String slot;
            String decoy;
            if (eastAsia != null && !EAST_DECOYS.isEmpty()) {
                decoy = EAST_DECOYS.stream().filter(f -> !f.equals(eastAsia)).findFirst().orElse("隶书");
                slot = "eastAsia";
            } else {
                decoy = LATIN_DECOYS.stream().filter(f -> !f.equals(family)).findFirst().orElse("Impact");
                slot = "family";
            }
            CTRPr rPr = target.getCTR().isSetRPr() ? target.getCTR().getRPr() : target.getCTR().addNewRPr();
            var fonts = rPr.sizeOfRFontsArray() > 0 ? rPr.getRFontsArray(0) : rPr.addNewRFonts();
            if ("eastAsia".equals(slot)) {
                fonts.setEastAsia(decoy);
            } else {
                fonts.setAscii(decoy);
                fonts.setHAnsi(decoy);
            }
            gt.add(new Injection(rule.getId(), "FONT", p.index(), slot + "→" + decoy));
            done++;
        }
    }

    private void injectLine(FormatRule rule, RuleConfig config, StyleSnapshot base,
                            XWPFDocument doc, List<Injection> gt) {
        Double multiplier = config.multiplier();
        StyleSnapshot.LineSpacing fixed = config.fixedLine();
        if (multiplier == null && fixed == null) {
            return;
        }
        int done = 0;
        for (ParagraphFact p : base.withSelector(rule.getTargetSelector())) {
            if (done >= CAP_PER_RULE) {
                return;
            }
            if (p.text() == null || p.text().isBlank()) {
                continue;
            }
            XWPFParagraph paragraph = bodyParagraph(doc, p.index());
            if (paragraph == null) {
                continue;
            }
            CTPPr pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
            CTSpacing spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
            spacing.setLineRule(STLineSpacingRule.AUTO);
            long twips = 240;
            if (multiplier != null && Math.abs(multiplier - 1.0) <= 0.05) {
                twips = 360; // 期望单倍时反向破坏为 1.5 倍
            }
            spacing.setLine(BigInteger.valueOf(twips));
            gt.add(new Injection(rule.getId(), "LINE_SPACING", p.index(),
                    "→AUTO " + twips + "/240 倍（期望 " + (multiplier != null ? multiplier : fixed) + "）"));
            done++;
        }
    }

    private void injectMargin(FormatRule rule, XWPFDocument doc, List<Injection> gt, ArrayNode skipped) {
        var body = doc.getDocument().getBody();
        if (!body.isSetSectPr() || !body.getSectPr().isSetPgMar()) {
            skipped.addObject().put("ruleId", rule.getId()).put("ruleType", "MARGIN")
                    .put("reason", "sectPr/pgMar 缺失，无法注入");
            return;
        }
        var mar = body.getSectPr().getPgMar();
        mar.setLeft(BigInteger.valueOf(parseTwips(mar.getLeft()) + 567)); // +1cm，远超 0.3mm 容差
        gt.add(new Injection(rule.getId(), "MARGIN", null, "左页边距 +1cm"));
    }

    private void injectCaption(FormatRule rule, StyleSnapshot base, XWPFDocument doc,
                               List<Injection> gt, ArrayNode skipped) {
        XWPFDocument probe = doc;
        for (ParagraphFact p : base.withSelector(rule.getTargetSelector())) {
            if (p.text() == null) {
                continue;
            }
            XWPFParagraph paragraph = bodyParagraph(probe, p.index());
            if (paragraph == null || !isCaptionStyled(paragraph, probe)) {
                continue; // 仅题注样式段落可破文本而不脱 selector
            }
            for (XWPFRun run : paragraph.getRuns()) {
                String t = run.getText(0);
                if (t == null || t.isBlank()) {
                    continue;
                }
                String broken = t.replaceFirst("[-－—.．]", "：");
                if (broken.equals(t)) {
                    continue;
                }
                run.setText(broken, 0);
                gt.add(new Injection(rule.getId(), "CAPTION", p.index(), "分隔符→全角冒号"));
                return;
            }
        }
        skipped.addObject().put("ruleId", rule.getId()).put("ruleType", "CAPTION")
                .put("reason", "无题注样式段落可用（纯文本题注破 pattern 即脱 selector，不硬造）");
    }

    private boolean isCaptionStyled(XWPFParagraph paragraph, XWPFDocument doc) {
        XWPFStyles styles = doc.getStyles();
        if (styles == null || paragraph.getStyle() == null) {
            return false;
        }
        XWPFStyle style = styles.getStyle(paragraph.getStyle());
        String name = style == null || style.getName() == null ? "" : style.getName().toLowerCase();
        return name.contains("caption") || name.contains("题注");
    }

    // ==================== 工具 ====================

    private static RunFact firstNonBlank(ParagraphFact p) {
        if (p.runs() == null) {
            return null;
        }
        return p.runs().stream()
                .filter(r -> r.text() != null && !r.text().isBlank())
                .findFirst().orElse(null);
    }

    /** 按提取器同源索引（bodyElements 序号）取段落；run 定位取 charStart 所属 run 的近似首 run。 */
    private static XWPFParagraph bodyParagraph(XWPFDocument doc, int bodyIndex) {
        int i = 0;
        for (IBodyElement element : doc.getBodyElements()) {
            if (i == bodyIndex && element instanceof XWPFParagraph paragraph) {
                return paragraph;
            }
            i++;
        }
        return null;
    }

    private static XWPFRun paragraphRun(XWPFDocument doc, int bodyIndex, int charStart) {
        XWPFParagraph paragraph = bodyParagraph(doc, bodyIndex);
        if (paragraph == null) {
            return null;
        }
        int cursor = 0;
        for (XWPFRun run : paragraph.getRuns()) {
            String t = run.getText(0) == null ? "" : run.getText(0);
            if (cursor == charStart && !t.isBlank()) {
                return run;
            }
            cursor += t.length();
        }
        // 兜底：首个有内容 run（跨 run 文本时 charStart 落点可能不齐）
        for (XWPFRun run : paragraph.getRuns()) {
            String t = run.getText(0) == null ? "" : run.getText(0);
            if (!t.isBlank()) {
                return run;
            }
        }
        return null;
    }

    private static void setRunSize(XWPFRun run, double pt) {
        if (run == null) {
            return;
        }
        CTRPr rPr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        BigInteger half = BigInteger.valueOf(Math.round(pt * 2));
        if (rPr.sizeOfSzArray() > 0) {
            rPr.getSzArray(0).setVal(half);
        } else {
            rPr.addNewSz().setVal(half);
        }
        if (rPr.sizeOfSzCsArray() > 0) {
            rPr.getSzCsArray(0).setVal(half);
        } else {
            rPr.addNewSzCs().setVal(half);
        }
    }

    private static long parseTwips(Object value) {
        return value == null ? 0 : Long.parseLong(String.valueOf(value).trim());
    }

    private static String formatPt(double pt) {
        return String.format("%.1fpt", pt);
    }

    private static Path repoDir() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("docs/test/fixtures/templates"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("未找到 docs/test/fixtures/templates");
        }
        return p;
    }
}
