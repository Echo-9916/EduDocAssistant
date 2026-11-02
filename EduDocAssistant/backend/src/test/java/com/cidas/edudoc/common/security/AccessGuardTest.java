package com.cidas.edudoc.common.security;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessGuardTest {

    private final AccessGuard guard = new AccessGuard();

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void permitsWhenPrincipalHasPermission() {
        UserPrincipal principal = new UserPrincipal("u1", "alice", null,
                Set.of("STUDENT"), Set.of("DOCUMENT_READ"), Set.of());

        assertDoesNotThrow(() -> AccessGuard.assertPermitted(principal, "DOCUMENT_READ"));
    }

    @Test
    void deniesWhenPermissionMissing() {
        UserPrincipal principal = new UserPrincipal("u1", "alice", null,
                Set.of("STUDENT"), Set.of(), Set.of());

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> AccessGuard.assertPermitted(principal, "DOCUMENT_READ"));
        assertEquals(ErrorCode.FORBIDDEN, e.errorCode());
    }

    @Test
    void guardProceedsWhenPermitted() throws Throwable {
        UserPrincipal principal = new UserPrincipal("u1", "alice", null,
                Set.of("STUDENT"), Set.of("DOCUMENT_READ"), Set.of());
        AuthContext.set(principal);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        RequirePermission permission = mock(RequirePermission.class);
        when(permission.value()).thenReturn("DOCUMENT_READ");
        when(pjp.proceed()).thenReturn("ok");

        assertEquals("ok", guard.guard(pjp, permission));
    }
}
