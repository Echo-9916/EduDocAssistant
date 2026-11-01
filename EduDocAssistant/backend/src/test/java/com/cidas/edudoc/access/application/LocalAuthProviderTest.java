package com.cidas.edudoc.access.application;

import com.cidas.edudoc.access.application.impl.LocalAuthProvider;
import com.cidas.edudoc.access.infrastructure.UserAccount;
import com.cidas.edudoc.access.infrastructure.UserAccountRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LocalAuthProviderTest {

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final LocalAuthProvider provider = new LocalAuthProvider(repository, encoder);

    @Test
    void lockedAccountRejected() {
        when(repository.findByUsername("u1")).thenReturn(Optional.of(
                new UserAccount("ABC", "u1", "Alice", "hash", "L", "LOCAL")));
        when(encoder.matches("pw", "hash")).thenReturn(true);

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> provider.authenticate("u1", "pw"));
        assertEquals(ErrorCode.UNAUTHORIZED, e.errorCode());
    }

    @Test
    void activeAccountReturnsPrincipal() {
        when(repository.findByUsername("u1")).thenReturn(Optional.of(
                new UserAccount("ABC", "u1", "Alice", "hash", "A", "LOCAL")));
        when(encoder.matches("pw", "hash")).thenReturn(true);
        when(repository.loadPrincipal("ABC", "u1")).thenReturn(
                new UserPrincipal("ABC", "u1", Set.of("STUDENT"), Set.of("DOCUMENT_READ"), Set.of()));

        Optional<UserPrincipal> result = provider.authenticate("u1", "pw");

        assertTrue(result.isPresent());
        assertEquals("ABC", result.get().userId());
    }

    @Test
    void ssoAccountNotAuthenticatedLocally() {
        when(repository.findByUsername("u1")).thenReturn(Optional.of(
                new UserAccount("ABC", "u1", "Alice", null, "A", "SSO")));

        assertTrue(provider.authenticate("u1", "pw").isEmpty());
        verifyNoInteractions(encoder);
    }

    @Test
    void realBcryptMatchesSeedPassword() {
        PasswordEncoder realEncoder = new BCryptPasswordEncoder();
        String hash = realEncoder.encode("Cidas@123");
        LocalAuthProvider realProvider = new LocalAuthProvider(repository, realEncoder);
        when(repository.findByUsername("student")).thenReturn(Optional.of(
                new UserAccount("ABC", "student", "学生测试", hash, "A", "LOCAL")));
        when(repository.loadPrincipal("ABC", "student")).thenReturn(
                new UserPrincipal("ABC", "student", Set.of("STUDENT"), Set.of("DOCUMENT_READ"), Set.of()));

        Optional<UserPrincipal> result = realProvider.authenticate("student", "Cidas@123");

        assertTrue(result.isPresent());
        assertEquals("ABC", result.get().userId());
    }
}
