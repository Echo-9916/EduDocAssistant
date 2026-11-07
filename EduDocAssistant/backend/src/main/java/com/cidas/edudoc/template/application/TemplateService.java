package com.cidas.edudoc.template.application;

import com.cidas.edudoc.common.api.PageResult;

/** 模板主档管理（D-01，详细设计表 3-6 前两接口 + 停用/删除辅助端点）。 */
public interface TemplateService {

    PageResult<TemplateView> list(ListQuery query);

    TemplateView create(CreateCommand command);

    void changeStatus(String templateId, String status);

    void deleteDraft(String templateId);

    record ListQuery(String courseId, String status, Integer page, Integer pageSize) {
    }

    record CreateCommand(String courseId, String templateName, String description) {
    }

    record TemplateView(String id, String courseId, String courseName, String templateName, String description,
                        String ownerId, String status, String currentVersionId, Integer currentVersionNo,
                        Integer ruleCount, String createTime, String updateTime) {
    }
}
