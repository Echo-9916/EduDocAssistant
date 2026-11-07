package com.cidas.edudoc.template.application;

import com.cidas.edudoc.template.application.TemplateVersionService.RuleCommand;
import com.cidas.edudoc.template.application.TemplateVersionService.TemplateVersionView;

import java.util.List;

/** 模板版本与规则集（D-02，详细设计表 3-6 versions/publish 接口 + 规则编辑辅助端点）。 */
public interface TemplateVersionService {

    TemplateVersionView create(String templateId, String changeSummary, byte[] fileBytes, String fileName);

    void replaceRules(String templateId, String versionId, List<RuleCommand> rules);

    void publish(String templateId, String versionId);

    List<TemplateVersionView> listVersions(String templateId);

    /** 1106 审查 P2-1：规则读取——管理端按版本回显（D-09 编辑页）。 */
    List<RuleView> listRules(String templateId, String versionId);

    /** 学生只读要求页（D-10）：课程内可读、仅已发布模板可见当前版本规则。 */
    List<RuleView> currentRules(String templateId);

    /** 规则编辑命令：ruleConfigJson 必须为合法 JSON（契约键集见任务文档 D-02）。 */
    record RuleCommand(String ruleType, String targetSelector, String severity, String autoFixable,
                       String ruleConfigJson, Integer ruleOrder) {
    }

    /** failureReason：解析失败原因经响应回显（表 3-23 无承载列，D-09 表单展示）。 */
    record TemplateVersionView(String id, String templateId, int versionNo, String validationStatus,
                               String publishedAt, String changeSummary, String sourceObjectId, int ruleCount,
                               String failureReason) {
    }

    record RuleView(String id, String ruleType, String targetSelector, String severity, String autoFixable,
                    String ruleConfigJson, int ruleOrder) {
    }
}
