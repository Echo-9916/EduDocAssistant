package com.cidas.edudoc.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserPrincipalTest {

    @Test
    void authoritiesPrefixRolesAndKeepPermissionsBare() {
        UserPrincipal p = new UserPrincipal("u1", "alice",
                Set.of("STUDENT"), Set.of("DOCUMENT_READ", "AI_QA"), Set.of());

        Set<String> authorities = p.authorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertEquals(Set.of("ROLE_STUDENT", "DOCUMENT_READ", "AI_QA"), authorities);
    }
}
