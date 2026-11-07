package com.cidas.edudoc.template.domain;

import com.cidas.edudoc.access.domain.RoleCode;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.template.infrastructure.Template;

/** 模板管理面可见性单源（D-01/D-02 共用）：OPS 全量、owner 本人、教师限任教课程；不可见 40401。 */
public final class TemplateAccess {

    private TemplateAccess() {
    }

    public static Template requireManageable(Template template, UserPrincipal principal) {
        if (template == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        boolean visible = principal.roles().contains(RoleCode.OPS)
                || template.getOwnerId().equalsIgnoreCase(principal.userId())
                || (principal.roles().contains(RoleCode.TEACHER) && principal.courseScope().stream()
                .anyMatch(course -> course.equalsIgnoreCase(template.getCourseId())));
        if (!visible) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        return template;
    }
}

