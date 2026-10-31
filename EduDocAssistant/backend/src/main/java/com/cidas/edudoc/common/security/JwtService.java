package com.cidas.edudoc.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** JWT 签发与解析。令牌只携带身份标识，角色/权限每次请求从库中实时加载，保证角色变更立即生效。 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long accessTtlSeconds;

    public JwtService(@Value("${edudoc.jwt.secret}") String secret,
                      @Value("${edudoc.jwt.access-ttl-seconds:1800}") long accessTtlSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String issue(String userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return new TokenClaims(claims.getSubject(), claims.get("username", String.class));
    }

    public record TokenClaims(String userId, String username) {
    }
}
