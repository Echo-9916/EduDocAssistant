package com.cidas.edudoc.template.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模板版本持久化对象（edudoc_template_version，发布后不可变）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateVersion {

    private String id;
    private String templateId;
    private int versionNo;
    private String sourceObjectId;
    private String validationStatus;
    private String publishedBy;
    private String publishedAt;
    private String changeSummary;
    /** 行内规则数子查询（listVersions 用，终审 Minor 消 N+1；selectById 不查则为 null）。 */
    private Integer ruleCount;
}
