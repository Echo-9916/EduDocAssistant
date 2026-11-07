package com.cidas.edudoc.template.domain;

/** 解析器产出的规则草稿（未落库形态，字段对齐 edudoc_format_rule）。 */
public record RuleDraft(String ruleType, String targetSelector, String severity, String autoFixable,
                        String ruleConfigJson, int ruleOrder) {
}
