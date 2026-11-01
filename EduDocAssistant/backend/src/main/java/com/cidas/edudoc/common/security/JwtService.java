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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** JWT 签发与解析。令牌携带身份与角色声明，鉴权仍以每次请求从库实时加载的角色/权限为准。 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long accessTtlSeconds;

    public JwtService(@Value("${edudoc.jwt.secret}") String secret,
                      @Value("${edudoc.jwt.access-ttl-seconds:1800}") long accessTtlSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String issue(String userId, String username, Set<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        List<?> rawRoles = claims.get("roles", List.class);
        Set<String> roles = rawRoles == null ? Set.of()
                : rawRoles.stream().map(Object::toString).collect(Collectors.toSet());
        return new TokenClaims(claims.getSubject(), claims.get("username", String.class), roles);
    }

    public record TokenClaims(String userId, String username, Set<String> roles) {
    }
}
