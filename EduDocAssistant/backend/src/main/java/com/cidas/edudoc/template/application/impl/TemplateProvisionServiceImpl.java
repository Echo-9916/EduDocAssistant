package com.cidas.edudoc.template.application.impl;

import com.cidas.edudoc.access.domain.RoleCode;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.application.DocumentProvisioningService;
import com.cidas.edudoc.document.domain.DocumentType;
import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.template.application.TemplateProvisionService;
import com.cidas.edudoc.template.infrastructure.FormatRuleRepository;
import com.cidas.edudoc.template.infrastructure.Template;
import com.cidas.edudoc.template.infrastructure.TemplateOutlineGenerator;
import com.cidas.edudoc.template.infrastructure.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * 按模板新建（D-07）：学生限本课程已发布模板；生成对象走服务端 putBytes（不经浏览器直传），
 * 建档复用 B-04 provision（同事务四写+审计+解析事件），随后同事务绑定 template_version_id（UT-13 后半）。
 */
@Service
@RequiredArgsConstructor
public class TemplateProvisionServiceImpl implements TemplateProvisionService {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final TemplateRepository templateRepository;
    private final FormatRuleRepository ruleRepository;
    private final OssStorageAdapter ossStorageAdapter;
    private final TemplateOutlineGenerator outlineGenerator;
    private final TemplateProvisionTxWriter provisionTxWriter;
    private final OssProperties ossProperties;

    /** 终审 I8：生成与 OSS 写入（网络/长 IO）在事务外，建档+绑定经 {@link TemplateProvisionTxWriter} 同事务。 */
    @Override
    public ProvisionView provision(String templateId, String title, String docType) {
        if (!Ids.isHex16(templateId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "模板 ID 格式不合法");
        }
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档标题需为 1-200 字符");
        }
        DocumentType type = parseDocType(docType);
        UserPrincipal principal = AuthContext.require();
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "模板不存在"));
        if (!"P".equals(template.getStatus()) || template.getCurrentVersionId() == null) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "模板未发布，暂不可用");
        }
        boolean inCourse = principal.courseScope().stream()
                .anyMatch(course -> course.equalsIgnoreCase(template.getCourseId()));
        if (!RoleCode.OPS.equals(primaryRole(principal)) && !inCourse) {
            throw new CidasBusinessException(ErrorCode.FORBIDDEN, "非模板所属课程成员");
        }

        byte[] docx = outlineGenerator.generate(title.trim(),
                ruleRepository.findByVersion(template.getCurrentVersionId()));
        String sessionId = Ids.hex16();
        String objectKey = "edudoc/generated/%s/%s.docx".formatted(template.getId(), sessionId);
        var info = ossStorageAdapter.putBytes(objectKey, docx, DOCX_MIME);
        String contentHash = sha256(docx);

        UploadSession session = new UploadSession(sessionId, principal.userId(), template.getCourseId(),
                title.trim(), type, title.trim() + ".docx", docx.length, DOCX_MIME, objectKey,
                UploadSession.Status.COMPLETING, null, null, Instant.now());
        DocumentProvisioningService.CreatedDocument created;
        try {
            created = provisionTxWriter.provisionAndBind(session, info, contentHash,
                    template.getCurrentVersionId());
        } catch (DuplicateKeyException e) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "同课程下已存在同名文档");
        }
        return new ProvisionView(created.documentId(), created.versionId(), template.getCurrentVersionId());
    }

    private static DocumentType parseDocType(String docType) {
        return Arrays.stream(DocumentType.values())
                .filter(value -> value.name().equals(docType))
                .findFirst()
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.BAD_REQUEST,
                        "文档类型不合法（课件/作业/论文/报告）"));
    }

    private static String primaryRole(UserPrincipal principal) {
        if (principal.roles().contains(RoleCode.OPS)) {
            return RoleCode.OPS;
        }
        if (principal.roles().contains(RoleCode.TEACHER)) {
            return RoleCode.TEACHER;
        }
        return RoleCode.STUDENT;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
