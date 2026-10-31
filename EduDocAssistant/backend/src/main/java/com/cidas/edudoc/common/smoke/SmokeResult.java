package com.cidas.edudoc.common.smoke;

import java.util.Map;

/** 冒烟聚合结果。 */
public record SmokeResult(String backend, DbStatus db, Map<String, Object> ai) {

    public record DbStatus(String status, int roleCount) {
    }
}
