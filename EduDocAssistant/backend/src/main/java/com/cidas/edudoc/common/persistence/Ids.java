package com.cidas.edudoc.common.persistence;

import java.util.UUID;

/** RAW(16) 主键在 Java 侧以 32 位十六进制大写串生成，经 HEXTORAW 落库（沿用 access 域先例）。 */
public final class Ids {

    private Ids() {
    }

    public static String hex16() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    /** 信任边界校验：RAW(16) 主键的 hex 串形态，非法值直接进 SQL 会触发 HEXTORAW 数据库异常。 */
    public static boolean isHex16(String value) {
        return value != null && value.matches("(?i)^[0-9a-f]{32}$");
    }
}
