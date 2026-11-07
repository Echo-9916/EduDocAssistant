package com.cidas.edudoc.template.application;

/** 按模板新建文档（D-07/J4）：生成大纲 docx → 服务端建档（同 B-04 事务口径）→ 绑定模板版本。 */
public interface TemplateProvisionService {

    ProvisionView provision(String templateId, String title, String docType);

    record ProvisionView(String documentId, String versionId, String templateVersionId) {
    }
}
