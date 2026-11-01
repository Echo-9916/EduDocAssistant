package com.cidas.edudoc.common.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenRevocationStoreTest {

    private final TokenRevocationStore store = new TokenRevocationStore();

    @Test
    void revokedJtiIsRejectedUntilExpiry() {
        store.revoke("jti-1", Instant.now().plusSeconds(60));
        assertTrue(store.isRevoked("jti-1"));
    }

    @Test
    void expiredRevocationIsForgotten() {
        store.revoke("jti-1", Instant.now().minusSeconds(1));
        assertFalse(store.isRevoked("jti-1"));
    }
}
