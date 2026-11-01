package com.cidas.edudoc.access.application;

import com.cidas.edudoc.access.application.impl.AuthServiceImpl;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.JwtService;
import com.cidas.edudoc.common.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final AuthProvider authProvider = mock(AuthProvider.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthService authService = new AuthServiceImpl(authProvider, jwtService);

    @Test
    void loginSuccessIssuesToken() {
        UserPrincipal principal = new UserPrincipal("ABC", "u1",
                Set.of("STUDENT"), Set.of("DOCUMENT_READ"), Set.of());
        when(authProvider.authenticate("u1", "pw")).thenReturn(Optional.of(principal));
        when(jwtService.issue("ABC", "u1", Set.of("STUDENT"))).thenReturn("tok");

        AuthService.LoginResult result = authService.login("u1", "pw");

        assertEquals("tok", result.token());
        assertEquals(principal, result.user());
    }

    @Test
    void loginBadCredentialsThrows40101() {
        when(authProvider.authenticate("u1", "wrong")).thenReturn(Optional.empty());

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> authService.login("u1", "wrong"));
        assertEquals(ErrorCode.UNAUTHORIZED, e.errorCode());
        verifyNoInteractions(jwtService);
    }

    @Test
    void loginBlankInputThrows40001() {
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> authService.login(" ", "pw"));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }
}
