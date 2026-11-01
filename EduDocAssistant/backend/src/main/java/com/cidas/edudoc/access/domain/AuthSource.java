package com.cidas.edudoc.access.domain;

/** 认证来源，与 edudoc_user.auth_source 一致。 */
public enum AuthSource {

    LOCAL("LOCAL"),
    SSO("SSO");

    private final String code;

    AuthSource(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
