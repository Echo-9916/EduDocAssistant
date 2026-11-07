package com.cidas.edudoc.template.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 格式问题持久化对象（edudoc_format_issue，fix_status OPEN/FIXED/IGNORED）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormatIssue {

    private String id;
    private String formatCheckId;
    private String ruleId;
    private String issueType;
    private String locationType;
    private String locationRef;
    private String expectedValue;
    private String actualValue;
    private String severity;
    private String autoFixable;
    private String fixStatus;
    private String guidance;
}
