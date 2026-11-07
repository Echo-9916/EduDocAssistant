package com.cidas.edudoc.template.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.access.domain.RoleCode;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.template.application.TemplateService;
import com.cidas.edudoc.template.infrastructure.Template;
import com.cidas.edudoc.template.infrastructure.TemplateRepository;
import com.cidas.edudoc.template.infrastructure.TemplateViewRow;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 模板主档（D-01）。可见性口径：
 * 学生只见已发布（且限课程范围内）；教师见「本人维护 ∪ 任教课程」；运维全量。
 * 删除采「仅无版本草稿可物理删、已发布只可停用」口径（任务文档 R9，表 3-22 无软删列）。
 */
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TemplateRepository templateRepository;
    private final AuditService auditService;

    @Override
    public PageResult<TemplateView> list(ListQuery query) {
        UserPrincipal principal = AuthContext.require();
        String role = primaryRole(principal);
        boolean unrestricted = RoleCode.OPS.equals(role);
        String status = RoleCode.STUDENT.equals(role) ? "P" : blankToNull(query.status());
        List<String> courseScope = List.copyOf(principal.courseScope());
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int pageSize = query.pageSize() == null || query.pageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.pageSize(), MAX_PAGE_SIZE);
        long offset = (long) (page - 1) * pageSize;
        String courseId = validateHex(query.courseId(), "courseId");
        if (!unrestricted && RoleCode.STUDENT.equals(role) && courseId != null
                && !containsIgnoreCase(courseScope, courseId)) {
            return new PageResult<>(List.of(), 0, page, pageSize);
        }
        long total = templateRepository.count(role, principal.userId(), courseScope, courseId,
                status, unrestricted);
        List<TemplateView> items = total == 0 ? List.of()
                : templateRepository.page(role, principal.userId(), courseScope, courseId,
                        status, unrestricted, offset, pageSize).stream()
                .map(TemplateServiceImpl::toView)
                .toList();
        return new PageResult<>(items, total, page, pageSize);
    }

    @Override
    public TemplateView create(CreateCommand command) {
        UserPrincipal principal = AuthContext.require();
        String courseId = validateHex(command.courseId(), "courseId");
        if (courseId == null) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "courseId 必填且需为合法 ID");
        }
        if (command.templateName() == null || command.templateName().isBlank()
                || command.templateName().length() > 200) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "模板名称需为 1-200 字符");
        }
        String role = primaryRole(principal);
        if (!RoleCode.OPS.equals(role)) {
            if (!RoleCode.TEACHER.equals(role)) {
                throw new CidasBusinessException(ErrorCode.FORBIDDEN, "仅教师或运维可维护模板");
            }
            if (!containsIgnoreCase(principal.courseScope(), courseId)) {
                throw new CidasBusinessException(ErrorCode.FORBIDDEN, "非本人任教课程");
            }
        }
        Template template = new Template(Ids.hex16(), courseId, command.templateName().trim(),
                command.description(), principal.userId(), null, "D");
        try {
            templateRepository.insert(template);
        } catch (DuplicateKeyException e) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "同课程下模板名称已存在");
        } catch (DataIntegrityViolationException e) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "课程不存在");
        }
        auditService.record("TEMPLATE_CREATED", "TEMPLATE", template.getId(), null, "200",
                Map.of("courseId", courseId, "templateName", template.getTemplateName()));
        return templateRepository.findView(template.getId())
                .map(TemplateServiceImpl::toView)
                .orElseGet(() -> toView(template));
    }

    @Override
    public void changeStatus(String templateId, String status) {
        if (!"P".equals(status) && !"A".equals(status)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "状态仅支持 P 发布 / A 停用");
        }
        Template template = loadVisible(validateHex(templateId, "模板 ID"));
        if ("P".equals(status)) {
            if (template.getCurrentVersionId() == null) {
                throw new CidasBusinessException(ErrorCode.CONFLICT, "模板尚未发布过任何版本，无法置为已发布");
            }
        } else if (!"P".equals(template.getStatus())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "仅已发布模板可停用");
        }
        templateRepository.updateStatus(template.getId(), status);
        auditService.record("TEMPLATE_STATUS_CHANGED", "TEMPLATE", template.getId(), null, "200",
                Map.of("from", template.getStatus(), "to", status));
    }

    @Override
    public void deleteDraft(String templateId) {
        Template template = loadVisible(validateHex(templateId, "模板 ID"));
        if (templateRepository.deleteDraft(template.getId()) == 0) {
            throw new CidasBusinessException(ErrorCode.CONFLICT,
                    "仅无任何版本的草稿可删除；已发布模板请改用停用");
        }
        auditService.record("TEMPLATE_DELETED", "TEMPLATE", template.getId(), null, "200",
                Map.of("templateName", template.getTemplateName()));
    }

    /** 学生永不直达管理接口（权限点拦截），此处兜底可见性：OPS 全量、owner 本人、教师限任教课程。 */
    private Template loadVisible(String templateIdHex) {
        UserPrincipal principal = AuthContext.require();
        Template template = templateRepository.findById(templateIdHex)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "模板不存在"));
        if (RoleCode.OPS.equals(primaryRole(principal))
                || template.getOwnerId().equalsIgnoreCase(principal.userId())
                || (RoleCode.TEACHER.equals(primaryRole(principal))
                && containsIgnoreCase(List.copyOf(principal.courseScope()), template.getCourseId()))) {
            return template;
        }
        throw new CidasBusinessException(ErrorCode.NOT_FOUND, "模板不存在");
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

    /** HEXTORAW 前拒绝非 hex（S2 口径）；空串视为无过滤。 */
    private static String validateHex(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!Ids.isHex16(value)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, label + " 格式不合法");
        }
        return value;
    }

    private static boolean containsIgnoreCase(java.util.Collection<String> list, String value) {
        return list.stream().anyMatch(item -> item.equalsIgnoreCase(value));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static TemplateView toView(TemplateViewRow row) {
        return new TemplateView(row.getId(), row.getCourseId(), row.getCourseName(), row.getTemplateName(),
                row.getDescription(), row.getOwnerId(), row.getStatus(), row.getCurrentVersionId(),
                row.getCurrentVersionNo(), row.getRuleCount(), row.getCreateTime(), row.getUpdateTime());
    }

    private static TemplateView toView(Template template) {
        return new TemplateView(template.getId(), template.getCourseId(), null, template.getTemplateName(),
                template.getDescription(), template.getOwnerId(), template.getStatus(),
                template.getCurrentVersionId(), null, 0, null, null);
    }
}
