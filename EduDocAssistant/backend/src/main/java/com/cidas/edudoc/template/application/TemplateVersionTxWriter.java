package com.cidas.edudoc.template.application;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.template.infrastructure.FormatRule;
import com.cidas.edudoc.template.infrastructure.FormatRuleRepository;
import com.cidas.edudoc.template.infrastructure.TemplateRepository;
import com.cidas.edudoc.template.infrastructure.TemplateVersion;
import com.cidas.edudoc.template.infrastructure.TemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 模板版本写事务（D-02）：OSS 写入与 POI 解析在事务外完成，本组件只做同库原子落写。
 * 发布后版本行与规则行不可变（表 3-23/3-24 语义 + UT-13）。
 */
@Component
@RequiredArgsConstructor
public class TemplateVersionTxWriter {

    private final OssObjectRepository ossObjectRepository;
    private final TemplateVersionRepository versionRepository;
    private final FormatRuleRepository ruleRepository;
    private final TemplateRepository templateRepository;
    private final AuditService auditService;

    @Transactional
    public void persistNewVersion(OssObject object, TemplateVersion version, List<FormatRule> rules) {
        if (object != null) {
            ossObjectRepository.insert(object);
        }
        try {
            versionRepository.insert(version);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 终审 Minor：并发建版本撞 uk_template_version_1 → 40901（OSS 孤儿对象留待清理任务，私有桶无害）
            throw new com.cidas.edudoc.common.api.CidasBusinessException(
                    com.cidas.edudoc.common.api.ErrorCode.CONFLICT, "并发生成模板版本冲突，请重试");
        }
        if (!rules.isEmpty()) {
            ruleRepository.insertBatch(rules);
        }
        auditService.record("TEMPLATE_VERSION_CREATED", "TEMPLATE_VERSION", version.getId(), null, "200",
                Map.of("templateId", version.getTemplateId(), "versionNo", version.getVersionNo(),
                        "validationStatus", version.getValidationStatus(), "ruleCount", rules.size()));
    }

    @Transactional
    public void replaceRules(String versionId, String templateId, List<FormatRule> rules, String validationStatus) {
        ruleRepository.deleteByVersion(versionId);
        ruleRepository.insertBatch(rules);
        versionRepository.updateValidation(versionId, validationStatus);
        auditService.record("TEMPLATE_RULES_UPDATED", "TEMPLATE_VERSION", versionId, null, "200",
                Map.of("templateId", templateId, "ruleCount", rules.size(),
                        "validationStatus", validationStatus));
    }

    @Transactional
    public void publish(String templateId, String versionId, String publisherId) {
        if (!versionRepository.markPublished(versionId, publisherId)) {
            // 终审 Minor：并发双发布——published_at IS NULL 守卫输者 40901，杜绝后写者静默胜
            throw new com.cidas.edudoc.common.api.CidasBusinessException(
                    com.cidas.edudoc.common.api.ErrorCode.CONFLICT, "该版本已被并发发布");
        }
        templateRepository.updateCurrentVersion(templateId, versionId);
        templateRepository.updateStatus(templateId, "P");
        auditService.record("TEMPLATE_PUBLISHED", "TEMPLATE", templateId, null, "200",
                Map.of("versionId", versionId));
    }
}
