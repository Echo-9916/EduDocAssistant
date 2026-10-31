package com.cidas.edudoc.common.security;

import com.cidas.edudoc.access.infrastructure.UserAccountRepository;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserAccountRepository repo = mock(UserAccountRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, repo);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        AuthContext.clear();
    }

    @Test
    void noTokenLeavesContextEmpty() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (r, s) -> {
                });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void validTokenSetsAuthentication() throws Exception {
        when(jwtService.parse("token")).thenReturn(new JwtService.TokenClaims("u1", "alice"));
        UserPrincipal principal = new UserPrincipal("u1", "alice",
                Set.of("STUDENT"), Set.of("DOCUMENT_READ"), Set.of());
        when(repo.loadPrincipal("u1", "alice")).thenReturn(principal);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer token");
        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> {
        });

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(principal, auth.getPrincipal());
    }

    @Test
    void invalidTokenClearsContext() throws Exception {
        when(jwtService.parse("bad")).thenThrow(new JwtException("expired"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer bad");
        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> {
        });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void lockedAccountClearsContext() throws Exception {
        when(jwtService.parse("token")).thenReturn(new JwtService.TokenClaims("u1", "alice"));
        when(repo.loadPrincipal("u1", "alice")).thenReturn(null);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer token");
        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> {
        });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
