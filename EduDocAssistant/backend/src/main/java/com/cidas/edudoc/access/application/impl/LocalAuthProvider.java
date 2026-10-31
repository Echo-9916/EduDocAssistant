package com.cidas.edudoc.access.application.impl;

import com.cidas.edudoc.access.application.AuthProvider;
import com.cidas.edudoc.access.infrastructure.UserAccount;
import com.cidas.edudoc.access.infrastructure.UserAccountRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 本地兼容认证：校验 BCrypt 口令与账号状态，SSO 账号不在此校验。 */
@Component
@RequiredArgsConstructor
public class LocalAuthProvider implements AuthProvider {

    private static final String SOURCE_LOCAL = "LOCAL";
    private static final String STATUS_ACTIVE = "A";

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Optional<UserPrincipal> authenticate(String username, String password) {
        Optional<UserAccount> account = repository.findByUsername(username)
                .filter(a -> SOURCE_LOCAL.equals(a.getAuthSource()))
                .filter(a -> a.getPasswordHash() != null
                        && passwordEncoder.matches(password, a.getPasswordHash()));
        if (account.isEmpty()) {
            return Optional.empty();
        }
        UserAccount found = account.get();
        if (!STATUS_ACTIVE.equals(found.getAccountStatus())) {
            throw new CidasBusinessException(ErrorCode.UNAUTHORIZED, "账号受限，请联系管理员");
        }
        return Optional.of(repository.loadPrincipal(found.getId(), found.getUsername()));
    }
}
