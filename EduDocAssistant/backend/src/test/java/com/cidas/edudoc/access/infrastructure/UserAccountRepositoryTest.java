package com.cidas.edudoc.access.infrastructure;

import com.cidas.edudoc.common.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAccountRepositoryTest {

    private final CourseRepository courseRepo = mock(CourseRepository.class);

    @Test
    void lockedAccountReturnsNullPrincipal() {
        UserAccountMapper mapper = mock(UserAccountMapper.class);
        when(mapper.selectAccountStatus("ABC")).thenReturn("L");
        UserAccountRepository repo = new UserAccountRepository(mapper, courseRepo);

        assertNull(repo.loadPrincipal("ABC", "u1"));
    }

    @Test
    void activeAccountLoadsRolesPermissionsAndCourseScope() {
        UserAccountMapper mapper = mock(UserAccountMapper.class);
        when(mapper.selectAccountStatus("ABC")).thenReturn("A");
        when(mapper.selectRoleCodes("ABC")).thenReturn(List.of("STUDENT"));
        when(mapper.selectPermissionCodes("ABC")).thenReturn(List.of("DOCUMENT_READ"));
        when(courseRepo.courseIdsOf("ABC")).thenReturn(Set.of("C1", "C2"));
        UserAccountRepository repo = new UserAccountRepository(mapper, courseRepo);

        UserPrincipal principal = repo.loadPrincipal("ABC", "u1");

        assertNotNull(principal);
        assertEquals(Set.of("STUDENT"), principal.roles());
        assertEquals(Set.of("DOCUMENT_READ"), principal.permissions());
        assertEquals(Set.of("C1", "C2"), principal.courseScope());
    }

    @Test
    void principalCarriesDisplayNameFromDb() {
        UserAccountMapper mapper = mock(UserAccountMapper.class);
        when(mapper.selectAccountStatus("ABC")).thenReturn("A");
        when(mapper.selectRoleCodes("ABC")).thenReturn(List.of("STUDENT"));
        when(mapper.selectPermissionCodes("ABC")).thenReturn(List.of());
        when(mapper.selectDisplayName("ABC")).thenReturn("学生测试");
        when(courseRepo.courseIdsOf("ABC")).thenReturn(Set.of());
        UserAccountRepository repo = new UserAccountRepository(mapper, courseRepo);

        UserPrincipal principal = repo.loadPrincipal("ABC", "u1");

        assertNotNull(principal);
        assertEquals("学生测试", principal.displayName());
    }
}
