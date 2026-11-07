package com.cidas.edudoc.template.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 格式规则 JSON 的单一领域解释（1107 第五轮 P2-1/P2-2 收口）：
 * 保存校验、比对、生成、修复四处共用同一套取值与不变量，消灭「保存接受、运行时跳过」的语义分叉。
 * 非法配置一律抛 IllegalArgumentException（保存期 40001、运行期 fail-closed 出「规则配置非法」问题）。
 */
public record RuleConfig(JsonNode expect, JsonNode fix) {

    /** 无共享 ObjectMapper 场景的兜底实例（线程安全，只读）。 */
    private static final ObjectMapper SHARED = new ObjectMapper();

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
        JsonNode fix = root.path("fix");
        validate(ruleType, expect);
        // P2-2 不变量：auto_fixable=Y 时 fix 必须携带同类型可执行值——由调用方按 fixable 决定是否走 fix 校验
        return new RuleConfig(expect, fix);
    }

    /** fix 侧完整性（auto_fixable=Y 的保存期与修复动作构建共用）。 */
    public boolean fixExecutable(String ruleType) {
        try {
            validate(ruleType, fix);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 修复动作消费视图：以 fix 子树为 expect 复用同一套类型化取值（不再二次校验）。 */
    public RuleConfig asFixView() {
        return new RuleConfig(fix, fix);
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
            case "SIZE" -> require(inRange(expect.path("pt").isNumber() ? expect.path("pt").asDouble() : -1,
                    0.5, 100), "SIZE.pt 必须为 (0,100] 磅值");
            case "LINE_SPACING" -> {
                boolean multiplier = inRange(expect.path("multiplier").isNumber()
                        ? expect.path("multiplier").asDouble() : -1, 0.1, 10);
                boolean fixed = validFixedLine(expect);
                require(multiplier || fixed,
                        "LINE_SPACING 需 multiplier(0,10] 或 lineRule(exact|atLeast)+pt(0,400]");
            }
            case "MARGIN" -> {
                Map<String, Double> values = pageValues(expect);
                require(!values.isEmpty(),
                        "MARGIN 需至少声明 top/bottom/left/right/width/height 之一（正值 mm）");
            }
            case "CAPTION" -> {
                boolean hasPattern = expect.path("pattern").isTextual();
                if (hasPattern) {
                    try {
                        Pattern.compile(expect.path("pattern").asText());
                    } catch (PatternSyntaxException e) {
                        throw new IllegalArgumentException("CAPTION.pattern 不是合法正则：" + e.getMessage());
                    }
                }
                boolean perChapter = expect.path("perChapter").isBoolean()
                        && expect.path("perChapter").asBoolean();
                require(hasPattern || perChapter, "CAPTION 需合法 pattern 或 perChapter=true（否则无可执行条件）");
            }
            case "HEADER_FOOTER" -> {
                if (!expect.path("present").isBoolean()) {
                    throw new IllegalArgumentException("HEADER_FOOTER.present 必须为 JSON 布尔");
                }
                require(expect.path("present").asBoolean(),
                        "HEADER_FOOTER 现仅支持「要求存在」语义（present=true）");
            }
            default -> {
                // CODE/REFERENCE 为存在性检查，无 expect 数值不变量
            }
        }
    }

    // ---- 类型化取值（比对/生成/修复消费侧） ----

    public String family() {
        return textOf(expect, "family");
    }

    public String eastAsia() {
        return textOf(expect, "eastAsia");
    }

    public Double pt() {
        return expect.path("pt").isNumber() ? expect.path("pt").asDouble() : null;
    }

    public Double tolerancePt() {
        JsonNode tolerance = expect.path("tolerance");
        return tolerance.isObject() && tolerance.path("pt").isNumber()
                ? tolerance.path("pt").asDouble() : null;
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
        String lowered = rule.toLowerCase(Locale.ROOT);
        String kind = lowered.startsWith("exact") ? "EXACT" : "AT_LEAST";
        return new StyleSnapshot.LineSpacing(kind, expect.path("pt").isNumber()
                ? expect.path("pt").asDouble() : null);
    }

    public boolean perChapter() {
        return expect.path("perChapter").isBoolean() && expect.path("perChapter").asBoolean();
    }

    public boolean present() {
        return expect.path("present").isBoolean() && expect.path("present").asBoolean();
    }

    public String pattern() {
        return textOf(expect, "pattern");
    }

    /** MARGIN 合法键值（top/bottom/left/right/width/height，mm）。 */
    public Map<String, Double> pageValues() {
        return pageValues(expect);
    }

    private static Map<String, Double> pageValues(JsonNode node) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String key : java.util.List.of("top", "bottom", "left", "right")) {
            putInRange(values, node, key, 0, 100);
        }
        putInRange(values, node, "width", 50, 1000);
        putInRange(values, node, "height", 50, 1000);
        return values;
    }

    private static void putInRange(Map<String, Double> into, JsonNode node, String key,
                                   double min, double max) {
        if (node.path(key).isNumber()) {
            double value = node.path(key).asDouble();
            if (value > min && value <= max) {
                into.put(key, value);
            }
        }
    }

    private static boolean validFixedLine(JsonNode expect) {
        String rule = textOf(expect, "lineRule");
        if (rule == null) {
            return false;
        }
        String lowered = rule.toLowerCase(Locale.ROOT);
        if (!lowered.startsWith("exact") && !lowered.startsWith("atleast") && !lowered.startsWith("at_least")) {
            throw new IllegalArgumentException("lineRule 仅支持 exact|atLeast，收到：" + rule);
        }
        return expect.path("pt").isNumber() && expect.path("pt").asDouble() > 0
                && expect.path("pt").asDouble() <= 400;
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

    private static boolean inRange(double value, double min, double max) {
        return value > min && value <= max;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
