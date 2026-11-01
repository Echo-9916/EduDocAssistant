package com.cidas.edudoc.common.security;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/** 权限切面：按固定鉴权顺序的「动作约束」步校验权限点，缺权限抛 40301。 */
@Aspect
@Component
public class AccessGuard {

    @Around("@annotation(requirePermission)")
    public Object guard(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        // 认证 → 账号状态 → 角色 已在 JwtAuthenticationFilter / UserAccountRepository 完成；
        // 课程范围本阶段为占位，待课程成员模块落地后按 principal.courseScope() 过滤。
        UserPrincipal principal = AuthContext.require();
        assertPermitted(principal, requirePermission.value());
        return joinPoint.proceed();
    }

    /** 校验当前主体是否持有指定权限点。 */
    static void assertPermitted(UserPrincipal principal, String permission) {
        if (principal == null || !principal.permissions().contains(permission)) {
            throw new CidasBusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
