package com.cidas.edudoc.template.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程模板持久化对象（edudoc_template，status：D 草稿、P 已发布、A 停用）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Template {

    private String id;
    private String courseId;
    private String templateName;
    private String description;
    private String ownerId;
    private String currentVersionId;
    private String status;
}
