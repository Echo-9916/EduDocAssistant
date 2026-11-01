package com.cidas.edudoc.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标注方法所需权限点；缺少时由 {@link AccessGuard} 拒绝（40301）。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 必需权限点编码，见 access.domain.PermissionCode。 */
    String value();
}
