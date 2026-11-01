package com.cidas.edudoc.access.domain;

/** 账号状态，与 edudoc_user.account_status 一致。 */
public enum AccountStatus {

    ACTIVE("A"),
    INACTIVE("I"),
    LOCKED("L"),
    DISABLED("D");

    private final String code;

    AccountStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
