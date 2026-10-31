package com.cidas.edudoc.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthContextTest {

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void requireThrowsWithoutPrincipal() {
        assertThrows(IllegalStateException.class, AuthContext::require);
    }

    @Test
    void setThenGetReturnsSamePrincipal() {
        UserPrincipal p = new UserPrincipal("u1", "alice", Set.of("STUDENT"), Set.of(), Set.of());

        AuthContext.set(p);

        assertSame(p, AuthContext.get());
        assertSame(p, AuthContext.require());
    }
}
