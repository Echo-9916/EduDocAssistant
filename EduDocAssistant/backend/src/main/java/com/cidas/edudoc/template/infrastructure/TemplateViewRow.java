package com.cidas.edudoc.template.infrastructure;

import lombok.Data;

/** 模板列表行（联查课程名、当前版本号与规则数）。 */
@Data
public class TemplateViewRow {

    private String id;
    private String courseId;
    private String courseName;
    private String templateName;
    private String description;
    private String ownerId;
    private String status;
    private String currentVersionId;
    private Integer currentVersionNo;
    private Integer ruleCount;
    private String createTime;
    private String updateTime;
}
