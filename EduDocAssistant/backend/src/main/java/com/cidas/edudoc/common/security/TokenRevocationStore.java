package com.cidas.edudoc.common.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** 已撤销令牌黑名单：按 jti 记录，TTL 与令牌剩余有效期一致。 */
@Component
public class TokenRevocationStore {

    // ponytail: 单机内存黑名单，重启即失；多节点部署需换 Redis
    private final ConcurrentHashMap<String, Instant> revoked = new ConcurrentHashMap<>();

    public void revoke(String jti, Instant expiresAt) {
        revoked.put(jti, expiresAt);
    }

    public boolean isRevoked(String jti) {
        Instant expiresAt = revoked.get(jti);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            revoked.remove(jti);
            return false;
        }
        return true;
    }
}
