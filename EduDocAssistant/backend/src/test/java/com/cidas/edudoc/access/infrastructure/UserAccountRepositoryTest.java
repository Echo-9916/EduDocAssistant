package com.cidas.edudoc.access.infrastructure;

import com.cidas.edudoc.common.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAccountRepositoryTest {

    @Test
    void lockedAccountReturnsNullPrincipal() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), any()))
                .thenReturn(List.of("L"));
        UserAccountRepository repo = new UserAccountRepository(jdbc);

        assertNull(repo.loadPrincipal("ABC", "u1"));
    }

    @Test
    void activeAccountLoadsRolesAndPermissions() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), any()))
                .thenReturn(List.of("A"), List.of("STUDENT"), List.of("DOCUMENT_READ"));
        UserAccountRepository repo = new UserAccountRepository(jdbc);

        UserPrincipal principal = repo.loadPrincipal("ABC", "u1");

        assertNotNull(principal);
        assertEquals(Set.of("STUDENT"), principal.roles());
        assertEquals(Set.of("DOCUMENT_READ"), principal.permissions());
    }
}
