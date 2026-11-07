package com.cidas.edudoc.template.domain;

/** 比对产出的问题草稿（未落库形态，字段对齐 edudoc_format_issue）。 */
public record IssueDraft(String ruleId, String issueType, String locationType, String locationRef,
                         String expectedValue, String actualValue, String severity, String autoFixable,
                         String guidance) {
}
