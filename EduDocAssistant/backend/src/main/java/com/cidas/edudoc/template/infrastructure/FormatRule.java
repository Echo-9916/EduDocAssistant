package com.cidas.edudoc.template.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 格式规则持久化对象（edudoc_format_rule，ruleConfigJson 为规则契约 JSON 串）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormatRule {

    private String id;
    private String templateVersionId;
    private String ruleType;
    private String targetSelector;
    private String severity;
    private String autoFixable;
    private String ruleConfigJson;
    private int ruleOrder;
}
