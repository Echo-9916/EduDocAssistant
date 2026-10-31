package com.cidas.edudoc.common.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET =
            "0123456789012345678901234567890123456789012345678901234567890123";

    @Test
    void issueAndParseRoundTrip() {
        JwtService service = new JwtService(SECRET, 3600);
        String token = service.issue("ABC123", "alice");
        JwtService.TokenClaims claims = service.parse(token);
        assertEquals("ABC123", claims.userId());
        assertEquals("alice", claims.username());
    }

    @Test
    void expiredTokenThrows() {
        JwtService service = new JwtService(SECRET, -1);
        String token = service.issue("ABC", "u");
        assertThrows(ExpiredJwtException.class, () -> service.parse(token));
    }
}
