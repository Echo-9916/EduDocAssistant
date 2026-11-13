package com.cidas.edudoc.access.domain;

/** 认证来源，与 edudoc_user.auth_source 一致。 */
public enum AuthSource {

    LOCAL("LOCAL"),
    SSO("SSO"),
    /** 名单导入生成的未激活账号，首登走统一身份认证。 */
    ACADEMIC("ACADEMIC");

    private final String code;

    AuthSource(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
