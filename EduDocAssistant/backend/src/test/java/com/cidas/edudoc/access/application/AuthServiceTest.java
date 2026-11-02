package com.cidas.edudoc.access.application;

import com.cidas.edudoc.access.application.impl.AuthServiceImpl;
import com.cidas.edudoc.access.infrastructure.AuditLogRepository;
import com.cidas.edudoc.access.infrastructure.UserAccountRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.JwtService;
import com.cidas.edudoc.common.security.TokenRevocationStore;
import com.cidas.edudoc.common.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final AuthProvider authProvider = mock(AuthProvider.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final TokenRevocationStore tokenRevocationStore = mock(TokenRevocationStore.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final AuthService authService = new AuthServiceImpl(authProvider, jwtService,
            userAccountRepository, auditLogRepository, tokenRevocationStore,
            new TransactionTemplate(txManager));

    @Test
    void loginSuccessUpdatesLastLoginAndAudits() {
        UserPrincipal principal = new UserPrincipal("ABC", "u1", null,
                Set.of("STUDENT"), Set.of("DOCUMENT_READ"), Set.of());
        when(authProvider.authenticate("u1", "pw")).thenReturn(Optional.of(principal));
        when(jwtService.issue("ABC", "u1", Set.of("STUDENT"))).thenReturn("tok");

        AuthService.LoginResult result = authService.login("u1", "pw");

        assertEquals("tok", result.token());
        assertEquals(principal, result.user());
        verify(userAccountRepository).updateLastLoginTime("ABC");
        verify(auditLogRepository).recordAuth("u1", "ABC", "SUCCESS");
        verify(txManager).commit(any());
    }

    @Test
    void loginTokenIssueFailureRollsBackWrites() {
        UserPrincipal principal = new UserPrincipal("ABC", "u1", null,
                Set.of("STUDENT"), Set.of(), Set.of());
        when(authProvider.authenticate("u1", "pw")).thenReturn(Optional.of(principal));
        when(jwtService.issue("ABC", "u1", Set.of("STUDENT"))).thenThrow(new IllegalStateException("sign failed"));

        assertThrows(IllegalStateException.class, () -> authService.login("u1", "pw"));

        // 故障注入：签发失败时成功写入整体回滚，不留「成功登录」虚假审计（P2-1）
        verify(txManager).rollback(any());
        verify(txManager, never()).commit(any());
    }

    @Test
    void loginBadCredentialsAudits40101() {
        when(authProvider.authenticate("u1", "wrong")).thenReturn(Optional.empty());

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> authService.login("u1", "wrong"));
        assertEquals(ErrorCode.UNAUTHORIZED, e.errorCode());
        verify(auditLogRepository).recordAuth("u1", null, "40101");
        verifyNoInteractions(jwtService);
        verify(userAccountRepository, never()).updateLastLoginTime(anyString());
    }

    @Test
    void loginLockedAccountAudits40101() {
        when(authProvider.authenticate("u1", "pw"))
                .thenThrow(new CidasBusinessException(ErrorCode.UNAUTHORIZED, "账号受限：账号已锁定，请联系管理员"));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> authService.login("u1", "pw"));
        assertEquals(ErrorCode.UNAUTHORIZED, e.errorCode());
        verify(auditLogRepository).recordAuth("u1", null, "40101");
        verifyNoInteractions(jwtService);
    }

    @Test
    void loginBlankInputSkipsAudit() {
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> authService.login(" ", "pw"));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
        verifyNoInteractions(auditLogRepository, userAccountRepository, jwtService);
    }

    @Test
    void logoutRevokesTokenJti() {
        JwtService.TokenClaims claims =
                new JwtService.TokenClaims("u1", "u1", Set.of(), "jti-1", Instant.now());
        when(jwtService.parse("tok")).thenReturn(claims);

        authService.logout("tok");

        verify(tokenRevocationStore).revoke("jti-1", claims.expiresAt());
    }
}
