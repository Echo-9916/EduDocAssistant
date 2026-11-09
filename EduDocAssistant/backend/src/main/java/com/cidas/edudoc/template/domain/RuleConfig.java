package com.cidas.edudoc.template.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 格式规则 JSON 的单一领域解释（1107 阶段审查 P1-2/P2-1 收口）：
 * 保存校验、比对、生成、修复四处共用同一套取值与不变量。
 * 契约位置固定为根级三键 {expect, tolerance?, fix?}（任务文档 D-02）——tolerance 不再从 expect 内读取。
 * 非法配置一律抛 IllegalArgumentException（保存期 40001、运行期 fail-closed 出「规则配置非法」问题）。
 */
public record RuleConfig(JsonNode expect, JsonNode tolerance, JsonNode fix) {

    private static final ObjectMapper SHARED = new ObjectMapper();
    private static final List<String> MARGIN_KEYS = List.of("top", "bottom", "left", "right");
    private static final List<String> PAGE_SIZE_KEYS = List.of("width", "height");

    public static RuleConfig parse(String ruleType, String configJson) {
        return parse(ruleType, configJson, SHARED);
    }

    public static RuleConfig parse(String ruleType, String configJson, ObjectMapper mapper) {
        JsonNode root;
        try {
            root = mapper.readTree(configJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("规则配置不是合法 JSON");
        }
        JsonNode expect = root.path("expect");
        JsonNode tolerance = root.path("tolerance");
        JsonNode fix = root.path("fix");
        validate(ruleType, expect);
        return new RuleConfig(expect, tolerance, fix);
    }

    /** 修复动作消费视图：以 fix 子树为 expect 复用同一套类型化取值（不再二次校验）。 */
    public RuleConfig asFixView() {
        return new RuleConfig(fix, tolerance, fix);
    }

    /** fix 侧完整性（auto_fixable=Y 保存闸）：fix 必须满足与 expect 同级的不变量。 */
    public boolean fixExecutable(String ruleType) {
        try {
            validate(ruleType, fix);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void validate(String ruleType, JsonNode expect) {
        switch (ruleType) {
            case "FONT" -> {
                boolean family = nonBlankText(expect, "family");
                boolean eastAsia = nonBlankText(expect, "eastAsia");
                if (expect.has("family") && !family) {
                    throw new IllegalArgumentException("FONT.family 必须为非空字符串");
                }
                if (expect.has("eastAsia") && !eastAsia) {
                    throw new IllegalArgumentException("FONT.eastAsia 必须为非空字符串");
                }
                require(family || eastAsia, "FONT 需至少声明 family(西文) 或 eastAsia(中文) 之一");
            }
            case "SIZE" -> require(numberInRange(expect, "pt", 0.5, 100), "SIZE.pt 必须为 (0,100] 磅值");
            case "LINE_SPACING" -> {
                boolean multiplier = numberInRange(expect, "multiplier", 0.1, 10);
                boolean fixed = isFixedLine(expect);
                require(multiplier || fixed,
                        "LINE_SPACING 需 multiplier(0,10] 或 lineRule(exact|atLeast)+pt(0,400]");
            }
            case "MARGIN" -> {
                // P2-1 收紧：出现的每个键必须是合法数值（0,100] 边距 / (50,1000] 纸型），禁止静默过滤
                for (String key : MARGIN_KEYS) {
                    if (expect.has(key)) {
                        require(numberInRange(expect, key, 0, 100), "MARGIN." + key + " 必须为 (0,100] mm");
                    }
                }
                for (String key : PAGE_SIZE_KEYS) {
                    if (expect.has(key)) {
                        require(numberInRange(expect, key, 50, 1000), "MARGIN." + key + " 必须为 (50,1000] mm");
                    }
                }
                boolean any = MARGIN_KEYS.stream().anyMatch(k -> expect.path(k).isNumber())
                        || PAGE_SIZE_KEYS.stream().anyMatch(k -> expect.path(k).isNumber());
                require(any, "MARGIN 需至少声明 top/bottom/left/right/width/height 之一");
            }
            case "CAPTION" -> {
                // P2-1 收紧：空串 pattern 归一为缺失；无任何可执行条件即拒绝
                String pattern = textOf(expect, "pattern");
                boolean perChapter = expect.path("perChapter").isBoolean() && expect.path("perChapter").asBoolean();
                if (pattern != null) {
                    try {
                        Pattern.compile(pattern);
                    } catch (PatternSyntaxException e) {
                        throw new IllegalArgumentException("CAPTION.pattern 不是合法正则：" + e.getMessage());
                    }
                }
                require(pattern != null || perChapter,
                        "CAPTION 需合法 pattern 或 perChapter=true（空字符串 pattern 视为未配置）");
            }
            case "HEADER_FOOTER" -> {
                if (!expect.path("present").isBoolean()) {
                    throw new IllegalArgumentException("HEADER_FOOTER.present 必须为 JSON 布尔");
                }
                require(expect.path("present").asBoolean(),
                        "HEADER_FOOTER 现仅支持「要求存在」语义（present=true）");
            }
            case "CODE" -> {
                require(nonBlankText(expect, "style"), "CODE 需声明 expect.style（模板代码样式）");
                if (expect.has("keepIndent") && !expect.path("keepIndent").isBoolean()) {
                    throw new IllegalArgumentException("CODE.keepIndent 必须为 JSON 布尔");
                }
            }
            case "REFERENCE" -> require(nonBlankText(expect, "standard"), "REFERENCE 需声明 expect.standard（著录标准）");
            default -> throw new IllegalArgumentException("未知规则类型：" + ruleType);
        }
    }

    // ---- 类型化取值（比对/生成/修复消费侧） ----

    public String family() {
        return textOf(expect, "family");
    }

    public String eastAsia() {
        return textOf(expect, "eastAsia");
    }

    /** P1-2：字体回退别名（解析器把「黑体;SimHei」的次名写入根级 tolerance.aliases）。 */
    public List<String> aliases() {
        List<String> list = new ArrayList<>();
        tolerance.path("aliases").forEach(node -> {
            if (node.isTextual() && !node.asText().isBlank()) {
                list.add(node.asText());
            }
        });
        return list;
    }

    public Double pt() {
        return expect.path("pt").isNumber() ? expect.path("pt").asDouble() : null;
    }

    /** P1-2：容差从根级 tolerance 读取（此前误读 expect.tolerance 导致自定义容差恒失效）。 */
    public Double tolerancePt() {
        return tolerance.path("pt").isNumber() ? tolerance.path("pt").asDouble() : null;
    }

    public Double multiplier() {
        return expect.path("multiplier").isNumber() ? expect.path("multiplier").asDouble() : null;
    }

    /** 固定行距：kind=EXACT|AT_LEAST，value=pt（可为 null）。 */
    public StyleSnapshot.LineSpacing fixedLine() {
        String rule = textOf(expect, "lineRule");
        if (rule == null) {
            return null;
        }
        return new StyleSnapshot.LineSpacing(fixedKind(rule),
                expect.path("pt").isNumber() ? expect.path("pt").asDouble() : null);
    }

    public boolean perChapter() {
        return expect.path("perChapter").isBoolean() && expect.path("perChapter").asBoolean();
    }

    public boolean present() {
        return expect.path("present").isBoolean() && expect.path("present").asBoolean();
    }

    /** 1108 复审 P2-1 残余：代码块保留缩进语义（keepIndent=true 时逐段消费）。 */
    public boolean keepIndent() {
        return expect.path("keepIndent").isBoolean() && expect.path("keepIndent").asBoolean();
    }

    public String pattern() {
        return textOf(expect, "pattern");
    }

    public Map<String, Double> pageValues() {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String key : MARGIN_KEYS) {
            if (expect.path(key).isNumber()) {
                values.put(key, expect.path(key).asDouble());
            }
        }
        for (String key : PAGE_SIZE_KEYS) {
            if (expect.path(key).isNumber()) {
                values.put(key, expect.path(key).asDouble());
            }
        }
        return values;
    }

    // ---- helpers ----

    private static boolean isFixedLine(JsonNode expect) {
        String rule = textOf(expect, "lineRule");
        if (rule == null) {
            return false;
        }
        String lowered = rule.toLowerCase(Locale.ROOT).replace("_", "");
        if (!lowered.startsWith("exact") && !lowered.startsWith("atleast")) {
            throw new IllegalArgumentException("lineRule 仅支持 exact|atLeast，收到：" + rule);
        }
        return expect.path("pt").isNumber() && expect.path("pt").asDouble() > 0
                && expect.path("pt").asDouble() <= 400;
    }

    private static String fixedKind(String rule) {
        return rule.toLowerCase(Locale.ROOT).startsWith("exact") ? "EXACT" : "AT_LEAST";
    }

    private static boolean numberInRange(JsonNode node, String key, double min, double max) {
        return node.path(key).isNumber() && node.path(key).asDouble() > min && node.path(key).asDouble() <= max;
    }

    private static boolean nonBlankText(JsonNode node, String field) {
        return textOf(node, field) != null;
    }

    private static String textOf(JsonNode node, String field) {
        if (!node.path(field).isTextual()) {
            return null;
        }
        String value = node.path(field).asText();
        return value.isBlank() ? null : value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
