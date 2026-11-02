package com.cidas.edudoc.common.persistence;

import java.util.UUID;

/** RAW(16) 主键在 Java 侧以 32 位十六进制大写串生成，经 HEXTORAW 落库（沿用 access 域先例）。 */
public final class Ids {

    private Ids() {
    }

    public static String hex16() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
