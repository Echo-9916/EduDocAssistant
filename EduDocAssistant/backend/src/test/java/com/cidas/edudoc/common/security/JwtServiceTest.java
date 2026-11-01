package com.cidas.edudoc.common.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET =
            "0123456789012345678901234567890123456789012345678901234567890123";

    @Test
    void issueAndParseRoundTrip() {
        JwtService service = new JwtService(SECRET, 3600);
        String token = service.issue("ABC123", "alice", Set.of("STUDENT", "TEACHER"));
        JwtService.TokenClaims claims = service.parse(token);
        assertEquals("ABC123", claims.userId());
        assertEquals("alice", claims.username());
        assertEquals(Set.of("STUDENT", "TEACHER"), claims.roles());
        assertNotNull(claims.jti());
        assertTrue(claims.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void expiredTokenThrows() {
        JwtService service = new JwtService(SECRET, -1);
        String token = service.issue("ABC", "u", Set.of());
        assertThrows(ExpiredJwtException.class, () -> service.parse(token));
    }
}
