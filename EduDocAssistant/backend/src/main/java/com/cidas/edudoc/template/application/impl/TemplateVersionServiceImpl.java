package com.cidas.edudoc.template.application.impl;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.template.application.TemplateVersionService;
import com.cidas.edudoc.template.application.TemplateVersionTxWriter;
import com.cidas.edudoc.template.domain.RuleConfig;
import com.cidas.edudoc.template.domain.RuleDraft;
import com.cidas.edudoc.template.domain.TemplateAccess;
import com.cidas.edudoc.template.domain.TemplateParseException;
import com.cidas.edudoc.template.infrastructure.FormatRule;
import com.cidas.edudoc.template.infrastructure.FormatRuleRepository;
import com.cidas.edudoc.template.infrastructure.Template;
import com.cidas.edudoc.template.infrastructure.TemplateRepository;
import com.cidas.edudoc.template.infrastructure.TemplateRuleParser;
import com.cidas.edudoc.template.infrastructure.TemplateVersion;
import com.cidas.edudoc.template.infrastructure.TemplateVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 模板版本解析、规则编辑与发布（D-02）。网络与解析在事务外，落库经 {@link TemplateVersionTxWriter}。 */
@Service
@RequiredArgsConstructor
public class TemplateVersionServiceImpl implements TemplateVersionService {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Set<String> RULE_TYPES =
            Set.of("FONT", "SIZE", "LINE_SPACING", "MARGIN", "CAPTION", "REFERENCE", "CODE", "HEADER_FOOTER");
    private static final Set<String> SEVERITIES = Set.of("BLOCKER", "WARNING", "INFO");
    /** 终审 I6：selector 词表单源（校验静默失效与按模板新建解析崩溃的根因防线）。 */
    private static final Map<String, Set<String>> SELECTORS = Map.of(
            "FONT", Set.of("HEADING_1", "HEADING_2", "HEADING_3", "BODY"),
            "SIZE", Set.of("HEADING_1", "HEADING_2", "HEADING_3", "BODY"),
            "LINE_SPACING", Set.of("HEADING_1", "HEADING_2", "HEADING_3", "BODY"),
            "MARGIN", Set.of("PAGE"),
            "HEADER_FOOTER", Set.of("HEADER", "FOOTER"),
            "CAPTION", Set.of("FIGURE_CAPTION", "TABLE_CAPTION"),
            "CODE", Set.of("CODE_BLOCK"),
            "REFERENCE", Set.of("REFERENCE"));
    /** R1 修复面四类：其余类型 auto_fixable 必须为 N（前端勾选与后端 skipped 不一致的根因）。 */
    private static final Set<String> FIXABLE_TYPES = Set.of("FONT", "SIZE", "LINE_SPACING", "MARGIN");

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository versionRepository;
    private final FormatRuleRepository ruleRepository;
    private final OssStorageAdapter ossStorageAdapter;
    private final TemplateRuleParser ruleParser;
    private final TemplateVersionTxWriter txWriter;
    private final OssProperties ossProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public TemplateVersionView create(String templateId, String changeSummary, byte[] fileBytes, String fileName) {
        Template template = requireManageable(templateId);
        // 复审 P2-3：模板版本必须携带 .docx 源文件（D-02 契约「上传并解析」，保证版本可追溯）
        boolean hasFile = fileBytes != null && fileBytes.length > 0;
        if (!hasFile) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "必须上传 .docx 模板文件");
        }
        if (fileName == null || !fileName.toLowerCase().endsWith(".docx")) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "模板文件仅支持 .docx");
        }
        int versionNo = versionRepository.maxVersionNo(template.getId()) + 1;
        String versionId = Ids.hex16();

        OssObject object = null;
        List<FormatRule> rules = List.of();
        String validation = "PENDING";
        String failureReason = null;
        if (hasFile) {
            String objectKey = "edudoc/templates/%s/%s/v%d.docx".formatted(monthDir(), template.getId(), versionNo);
            var info = ossStorageAdapter.putBytes(objectKey, fileBytes, DOCX_MIME);
            object = new OssObject(Ids.hex16(), ossProperties.bucket(), ossProperties.region(), objectKey,
                    info.versionId(), info.etag(), sha256(fileBytes), info.contentLength(), DOCX_MIME,
                    info.encryption() == null ? "NONE" : info.encryption(), "ORIGINAL");
            try {
                rules = ruleParser.parse(fileBytes).stream()
                        .map(draft -> toEntity(draft, versionId))
                        .toList();
                validation = rules.isEmpty() ? "PENDING" : "VALID";
            } catch (TemplateParseException e) {
                validation = "INVALID";
                failureReason = e.getMessage();
            }
        }
        TemplateVersion version = new TemplateVersion(versionId, template.getId(), versionNo,
                object == null ? null : object.getId(), validation, null, null, changeSummary, null,
                failureReason);
        txWriter.persistNewVersion(object, version, rules);
        return new TemplateVersionView(versionId, template.getId(), versionNo, validation, null,
                changeSummary, version.getSourceObjectId(), rules.size(), failureReason);
    }

    @Override
    public void replaceRules(String templateId, String versionId, List<RuleCommand> commands) {
        Template template = requireManageable(templateId);
        TemplateVersion version = loadOwnedVersion(template, versionId);
        if (version.getPublishedAt() != null) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "已发布版本不可修改，请新建版本");
        }
        List<FormatRule> rules = commands.stream().map(command -> {
            if (!RULE_TYPES.contains(command.ruleType())) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "规则类型不合法：" + command.ruleType());
            }
            if (!SEVERITIES.contains(command.severity())) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "严重程度不合法：" + command.severity());
            }
            if (!"Y".equals(command.autoFixable()) && !"N".equals(command.autoFixable())) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "autoFixable 仅支持 Y/N");
            }
            if (command.targetSelector() == null || command.targetSelector().isBlank()) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "targetSelector 必填");
            }
            Set<String> allowedSelectors = SELECTORS.get(command.ruleType());
            if (allowedSelectors == null || !allowedSelectors.contains(command.targetSelector().trim())) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST,
                        command.ruleType() + " 的 targetSelector 不在词表：" + command.targetSelector());
            }
            if ("Y".equals(command.autoFixable()) && !FIXABLE_TYPES.contains(command.ruleType())) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST,
                        "仅字体/字号/行距/页面设置（边距+纸型）规则可标记自动修复（R1 修复面）");
            }
            if (!isValidJson(command.ruleConfigJson())) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "ruleConfigJson 必须为合法 JSON");
            }
            // 第五轮 P2-1/P2-2 收口：规则 JSON 经 RuleConfig 单点校验完整不变量
            // （数值范围、枚举域、JSON 布尔、CAPTION 可执行性、正则合法性），
            // 且 auto_fixable=Y ⇒ fix 子树同样可执行；非法配置保存即 40001，运行时不再静默跳过
            try {
                RuleConfig config = RuleConfig.parse(command.ruleType(),
                        command.ruleConfigJson(), objectMapper);
                if ("Y".equals(command.autoFixable()) && !config.fixExecutable(command.ruleType())) {
                    throw new CidasBusinessException(ErrorCode.BAD_REQUEST,
                            command.ruleType() + " 标记 auto_fixable=Y 但 fix 缺失或非法（前端将显示不可修复）");
                }
            } catch (IllegalArgumentException invalid) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, invalid.getMessage());
            }
            return new FormatRule(Ids.hex16(), version.getId(), command.ruleType(),
                    command.targetSelector().trim(), command.severity(), command.autoFixable(),
                    command.ruleConfigJson(), command.ruleOrder() == null ? 0 : command.ruleOrder());
        }).toList();
        txWriter.replaceRules(version.getId(), template.getId(), rules,
                rules.isEmpty() ? "PENDING" : "VALID");
    }

    @Override
    public void publish(String templateId, String versionId) {
        Template template = requireManageable(templateId);
        TemplateVersion version = loadOwnedVersion(template, versionId);
        if (version.getPublishedAt() != null) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "该版本已发布，历史版本不可重复发布");
        }
        if (!"VALID".equals(version.getValidationStatus())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "版本规则尚未解析成功或未完成编辑（"
                    + version.getValidationStatus() + "），不能发布");
        }
        txWriter.publish(template.getId(), version.getId(), AuthContext.require().userId());
    }

    @Override
    public List<TemplateVersionView> listVersions(String templateId) {
        Template template = requireManageable(templateId);
        return versionRepository.listByTemplate(template.getId()).stream()
                .map(version -> new TemplateVersionView(version.getId(), version.getTemplateId(),
                        version.getVersionNo(), version.getValidationStatus(), version.getPublishedAt(),
                        version.getChangeSummary(), version.getSourceObjectId(),
                        version.getRuleCount() == null ? 0 : version.getRuleCount(),
                        // 1107 P2-2：INVALID 原因持久化后重开编辑页仍可解释
                        version.getParseError()))
                .toList();
    }

    @Override
    public List<RuleView> listRules(String templateId, String versionId) {
        Template template = requireManageable(templateId);
        TemplateVersion version = loadOwnedVersion(template, versionId);
        return ruleRepository.findByVersion(version.getId()).stream()
                .map(TemplateVersionServiceImpl::toRuleView)
                .toList();
    }

    /** 只读可见性（1106 审查 P2-1）：OPS/owner/任教课程或课程范围内学生可读；未发布 40901。 */
    @Override
    public List<RuleView> currentRules(String templateId) {
        if (!Ids.isHex16(templateId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "模板 ID 格式不合法");
        }
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "模板不存在"));
        if (!"P".equals(template.getStatus()) || template.getCurrentVersionId() == null) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "模板未发布，暂无可用规则集");
        }
        UserPrincipal principal = AuthContext.require();
        boolean visible = principal.roles().contains(com.cidas.edudoc.access.domain.RoleCode.OPS)
                || template.getOwnerId().equalsIgnoreCase(principal.userId())
                || principal.courseScope().stream()
                .anyMatch(course -> course.equalsIgnoreCase(template.getCourseId()));
        if (!visible) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        return ruleRepository.findByVersion(template.getCurrentVersionId()).stream()
                .map(TemplateVersionServiceImpl::toRuleView)
                .toList();
    }

    private static RuleView toRuleView(FormatRule rule) {
        return new RuleView(rule.getId(), rule.getRuleType(), rule.getTargetSelector(), rule.getSeverity(),
                rule.getAutoFixable(), rule.getRuleConfigJson(), rule.getRuleOrder());
    }

    private Template requireManageable(String templateId) {
        if (!Ids.isHex16(templateId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "模板 ID 格式不合法");
        }
        UserPrincipal principal = AuthContext.require();
        return TemplateAccess.requireManageable(templateRepository.findById(templateId).orElse(null), principal);
    }

    private TemplateVersion loadOwnedVersion(Template template, String versionId) {
        if (!Ids.isHex16(versionId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "版本 ID 格式不合法");
        }
        TemplateVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "模板版本不存在"));
        if (!version.getTemplateId().equalsIgnoreCase(template.getId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "模板版本不存在");
        }
        return version;
    }

    private static FormatRule toEntity(RuleDraft draft, String versionId) {
        return new FormatRule(Ids.hex16(), versionId, draft.ruleType(), draft.targetSelector(),
                draft.severity(), draft.autoFixable(), draft.ruleConfigJson(), draft.ruleOrder());
    }

    private static String monthDir() {
        return LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private boolean isValidJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            objectMapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

}
