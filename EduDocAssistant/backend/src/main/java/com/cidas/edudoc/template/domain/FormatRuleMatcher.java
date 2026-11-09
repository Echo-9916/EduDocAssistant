package com.cidas.edudoc.template.domain;

import com.cidas.edudoc.template.domain.StyleSnapshot.LineSpacing;
import com.cidas.edudoc.template.domain.StyleSnapshot.PageFacts;
import com.cidas.edudoc.template.domain.StyleSnapshot.ParagraphFact;
import com.cidas.edudoc.template.domain.StyleSnapshot.RunFact;
import com.cidas.edudoc.template.infrastructure.FormatRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 模板规则比对（详细设计 §3.3.2 check 伪代码）：按 rule_order 逐规则选靶、
 * 按 run 级生效值比较、不符产出问题行。1107 第五轮：规则 JSON 统一经 {@link RuleConfig}
 * 解释（与保存校验/生成/修复同源）；无法解释的历史脏配置**失败关闭**产出问题，不再静默跳过。
 */
@Component
@RequiredArgsConstructor
public class FormatRuleMatcher {

    private static final Pattern CHAPTER_CAPTION = Pattern.compile("^[图表]\\s*\\d+\\s*[-－.]\\s*\\d+.*");
    private static final double MM_TOLERANCE = 0.3;
    private static final double PAGE_TOLERANCE = 3.0;
    private static final double LINE_TOLERANCE = 0.05;
    private static final double PT_TOLERANCE_DEFAULT = 0.5;

    private final ObjectMapper mapper;

    public List<IssueDraft> match(List<FormatRule> rules, StyleSnapshot snapshot) {
        List<IssueDraft> issues = new ArrayList<>();
        for (FormatRule rule : rules) {
            RuleConfig config;
            try {
                config = RuleConfig.parse(rule.getRuleType(), rule.getRuleConfigJson(), mapper);
            } catch (IllegalArgumentException invalid) {
                issues.add(new IssueDraft(rule.getId(), rule.getRuleType(), "ELEMENT", "{}",
                        "-", "规则配置非法：" + invalid.getMessage(), "WARNING", "N",
                        "该规则无法执行，请在模板编辑器中重新配置"));
                continue;
            }
            switch (rule.getRuleType()) {
                case "FONT" -> matchFont(rule, config, snapshot, issues);
                case "SIZE" -> matchSize(rule, config, snapshot, issues);
                case "LINE_SPACING" -> matchLineSpacing(rule, config, snapshot, issues);
                case "MARGIN" -> matchMargin(rule, config, snapshot.page(), issues);
                case "HEADER_FOOTER" -> matchHeaderFooter(rule, snapshot.page(), issues);
                case "CAPTION" -> matchCaption(rule, config, snapshot, issues);
                case "REFERENCE" -> matchReference(rule, snapshot, issues);
                case "CODE" -> matchCode(rule, config, snapshot, issues);
                default -> {
                    // 未知类型在配置非法分支已兜底，这里不动作
                }
            }
        }
        return issues;
    }

    private void matchFont(FormatRule rule, RuleConfig config, StyleSnapshot snapshot, List<IssueDraft> out) {
        String expect = config.family();
        String expectEastAsia = config.eastAsia();
        // 1107 P1-2：消费根级 tolerance.aliases——字体回退链次名（如 SimHei）视为符合主名期望
        List<String> aliases = config.aliases();
        for (ParagraphFact p : snapshot.withSelector(rule.getTargetSelector())) {
            if (expect != null) {
                violation(rule, p, "family", expect, aliases, out,
                        p::familyOf, "将该段落西文字体设置为「" + expect + "」");
            }
            if (expectEastAsia != null) {
                violation(rule, p, "eastAsia", expectEastAsia, aliases, out,
                        p::eastAsiaOf, "将该段落中文字体设置为「" + expectEastAsia + "」");
            }
        }
    }

    /**
     * run 级违规收集（P2-5）：逐 run 计算生效值，收集去重后的违规值；
     * 「未声明」与具体错误值分别成条，location 取首个违规 run 的字符区间（P3-1）。
     */
    private void violation(FormatRule rule, ParagraphFact p, String slot, String expect,
                           List<String> aliases, List<IssueDraft> out,
                           java.util.function.Function<RunFact, String> effective,
                           String guidance) {
        Set<String> offenders = new LinkedHashSet<>();
        RunFact first = null;
        for (RunFact run : p.effectiveRuns()) {
            if (ParagraphFact.blank(run)) {
                continue;
            }
            String value = effective.apply(run);
            boolean ok = value != null && (expect.equalsIgnoreCase(value)
                    || aliases.stream().anyMatch(a -> a.equalsIgnoreCase(value)));
            if (value == null) {
                offenders.add(slot.equals("eastAsia") ? "中文槽未声明" : "西文槽未声明");
            } else if (!ok) {
                offenders.add(value);
            }
            if (first == null && !ok) {
                first = run;
            }
        }
        if (offenders.isEmpty()) {
            return;
        }
        String expected = slot.equals("eastAsia") ? "中文字体 " + expect : expect;
        out.add(new IssueDraft(rule.getId(), "FONT", "PARAGRAPH", location(p, first),
                expected, String.join(",", offenders), rule.getSeverity(), rule.getAutoFixable(), guidance));
    }

    private void matchSize(FormatRule rule, RuleConfig config, StyleSnapshot snapshot, List<IssueDraft> out) {
        Double expect = config.pt();
        double tolerance = config.tolerancePt() == null ? PT_TOLERANCE_DEFAULT : config.tolerancePt();
        for (ParagraphFact p : snapshot.withSelector(rule.getTargetSelector())) {
            Set<String> offenders = new LinkedHashSet<>();
            RunFact first = null;
            for (RunFact run : p.effectiveRuns()) {
                if (ParagraphFact.blank(run)) {
                    continue;
                }
                Double value = p.ptOf(run);
                if (value == null) {
                    offenders.add("字号未声明");
                } else if (Math.abs(value - expect) > tolerance + 1e-9) {
                    offenders.add(formatPt(value));
                }
                if (first == null && (value == null || Math.abs(value - expect) > tolerance + 1e-9)) {
                    first = run;
                }
            }
            if (!offenders.isEmpty()) {
                out.add(new IssueDraft(rule.getId(), "SIZE", "PARAGRAPH", location(p, first),
                        formatPt(expect), String.join(",", offenders), rule.getSeverity(),
                        rule.getAutoFixable(), "将该段落字号设置为 " + formatPt(expect)));
            }
        }
    }

    private void matchLineSpacing(FormatRule rule, RuleConfig config, StyleSnapshot snapshot,
                                  List<IssueDraft> out) {
        Double expectMultiplier = config.multiplier();
        LineSpacing expectFixed = config.fixedLine();
        for (ParagraphFact p : snapshot.withSelector(rule.getTargetSelector())) {
            if (p.text() == null || p.text().isBlank()) {
                continue;
            }
            LineSpacing line = p.line();
            String actual = describeLine(line);
            boolean pass;
            if (expectMultiplier != null) {
                pass = line != null && !line.fixed() && line.value() != null
                        && Math.abs(line.value() - expectMultiplier) <= LINE_TOLERANCE;
                if (pass) {
                    continue;
                }
                out.add(new IssueDraft(rule.getId(), "LINE_SPACING", "PARAGRAPH", location(p, null),
                        formatMultiplier(expectMultiplier) + " 倍", actual,
                        rule.getSeverity(), rule.getAutoFixable(),
                        "将该段落行距设置为 " + formatMultiplier(expectMultiplier) + " 倍"));
            } else {
                pass = line != null && expectFixed != null
                        && expectFixed.kind().equals(line.kind())
                        && (expectFixed.value() == null
                        || (line.value() != null && Math.abs(line.value() - expectFixed.value()) <= 0.6));
                if (!pass) {
                    out.add(new IssueDraft(rule.getId(), "LINE_SPACING", "PARAGRAPH", location(p, null),
                            "固定" + (expectFixed.value() == null ? "" : formatPt(expectFixed.value()))
                                    + "磅（" + expectFixed.kind() + "）",
                            actual, rule.getSeverity(), rule.getAutoFixable(),
                            "将该段落行距设置为固定磅值"));
                }
            }
        }
    }

    private void matchMargin(FormatRule rule, RuleConfig config, PageFacts page, List<IssueDraft> out) {
        List<String> mismatches = new ArrayList<>();
        List<String> actuals = new ArrayList<>();
        for (Map.Entry<String, Double> entry : config.pageValues().entrySet()) {
            String key = entry.getKey();
            double want = entry.getValue();
            Double got = pageValue(page, key);
            double tolerance = key.equals("width") || key.equals("height") ? PAGE_TOLERANCE : MM_TOLERANCE;
            if (got == null) {
                mismatches.add(key + "=" + trimMm(want));
                actuals.add(key + "=未声明");
            } else if (Math.abs(got - want) > tolerance) {
                mismatches.add(key + "=" + trimMm(want));
                actuals.add(key + "=" + trimMm(got));
            }
        }
        if (!mismatches.isEmpty()) {
            out.add(new IssueDraft(rule.getId(), "MARGIN", "PAGE", "{\"page\":1}",
                    String.join(",", mismatches), String.join(",", actuals),
                    rule.getSeverity(), rule.getAutoFixable(), "页面布局→页边距/纸张：按期望值设置（mm）"));
        }
    }

    private void matchHeaderFooter(FormatRule rule, PageFacts page, List<IssueDraft> out) {
        boolean header = "HEADER".equals(rule.getTargetSelector());
        boolean present = header ? page.headerPresent() : page.footerPresent();
        if (!present) {
            out.add(new IssueDraft(rule.getId(), "HEADER_FOOTER", "PAGE", "{\"page\":1}",
                    header ? "存在页眉" : "存在页脚", "缺失", rule.getSeverity(), rule.getAutoFixable(),
                    "插入" + (header ? "页眉" : "页脚") + "并填写课程要求内容"));
        }
    }

    private void matchCaption(FormatRule rule, RuleConfig config, StyleSnapshot snapshot, List<IssueDraft> out) {
        String pattern = config.pattern();
        Pattern compiled = null;
        if (pattern != null) {
            try {
                compiled = Pattern.compile(pattern);
            } catch (Exception e) {
                compiled = null; // 保存闸已拦截；此处退回 perChapter 语义
            }
        }
        boolean perChapter = config.perChapter();
        if (compiled == null && !perChapter) {
            return;
        }
        for (ParagraphFact p : snapshot.withSelector(rule.getTargetSelector())) {
            if (p.text() == null) {
                continue;
            }
            boolean pass = compiled != null ? compiled.matcher(p.text()).find()
                    : CHAPTER_CAPTION.matcher(p.text()).matches();
            if (!pass) {
                out.add(new IssueDraft(rule.getId(), "CAPTION", "PARAGRAPH", location(p, null),
                        compiled != null ? pattern : "章-序编号（如 " + ("TABLE_CAPTION"
                        .equals(rule.getTargetSelector()) ? "表2-1" : "图2-1") + "）",
                        p.text(), rule.getSeverity(), rule.getAutoFixable(),
                        "使用「引用→题注」并按模板编号形式修改"));
            }
        }
    }

    private void matchReference(FormatRule rule, StyleSnapshot snapshot, List<IssueDraft> out) {
        boolean present = snapshot.paragraphs().stream()
                .anyMatch(p -> p.text() != null && "参考文献".equals(p.text().strip()));
        if (!present) {
            out.add(new IssueDraft(rule.getId(), "REFERENCE", "REFERENCE", "{\"page\":1}",
                    "存在「参考文献」章节", "缺失", rule.getSeverity(), rule.getAutoFixable(),
                    "补充「参考文献」章节并按 GB/T 7714 著录"));
        }
    }

    /**
     * CODE 规则消费 style+keepIndent（1108 复审 P2-1 残余）：
     * style 语义已由提取端样式分类承载（CODE_BLOCK selector 即按代码样式归类，样式 id 跨文档不可比，
     * 文档已注释）；keepIndent=true 时逐代码段检查首 run 原文是否保留行首缩进，
     * 缩进丢失逐段报告（run 明细缺失的段落证据不足不报，宁缺勿误）。
     */
    private void matchCode(FormatRule rule, RuleConfig config, StyleSnapshot snapshot, List<IssueDraft> out) {
        List<ParagraphFact> codes = snapshot.withSelector("CODE_BLOCK");
        if (codes.isEmpty()) {
            out.add(new IssueDraft(rule.getId(), "CODE", "ELEMENT", "{}",
                    "存在模板定义的代码块样式段落", "缺失", rule.getSeverity(), rule.getAutoFixable(),
                    "代码段落请应用模板定义的代码样式（保留缩进）"));
            return;
        }
        if (!config.keepIndent()) {
            return;
        }
        for (ParagraphFact p : codes) {
            if (p.runs() == null || p.runs().isEmpty()) {
                continue; // 无 run 原文证据，不据此报
            }
            RunFact firstContent = null;
            for (RunFact run : p.runs()) {
                if (run.text() != null && !run.text().isBlank()) {
                    firstContent = run;
                    break;
                }
            }
            if (firstContent == null) {
                continue;
            }
            String head = firstContent.text();
            if (!(head.startsWith(" ") || head.startsWith("\t"))) {
                out.add(new IssueDraft(rule.getId(), "CODE", "PARAGRAPH", location(p, firstContent),
                        "代码段保留行首缩进", head.strip(),
                        rule.getSeverity(), "N", "重新粘贴代码并保留缩进（或应用代码样式段落缩进）"));
            }
        }
    }

    private static String location(ParagraphFact p, RunFact run) {
        int start = run != null ? run.charStart() : 0;
        int end = run != null ? run.charEnd() : (p.text() == null ? 0 : p.text().length());
        return "{\"paragraphIndex\":" + p.index() + ",\"charStart\":" + start + ",\"charEnd\":" + end + "}";
    }

    private static String describeLine(LineSpacing line) {
        if (line == null) {
            return "行距未声明";
        }
        return line.fixed()
                ? "固定" + formatMultiplier(line.value() == null ? 0 : line.value()) + "磅"
                : formatMultiplier(line.value() == null ? 0 : line.value()) + " 倍";
    }

    private static Double pageValue(PageFacts page, String key) {
        return switch (key) {
            case "top" -> page.top();
            case "bottom" -> page.bottom();
            case "left" -> page.left();
            case "right" -> page.right();
            case "width" -> page.widthMm();
            case "height" -> page.heightMm();
            default -> null;
        };
    }

    private static String formatPt(double pt) {
        return String.format("%.1fpt", pt);
    }

    private static String formatMultiplier(double value) {
        return String.format("%.1f", value);
    }

    private static String trimMm(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
