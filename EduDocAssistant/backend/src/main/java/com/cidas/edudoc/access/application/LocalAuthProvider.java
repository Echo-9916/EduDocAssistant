package com.cidas.edudoc.access.application;

import com.cidas.edudoc.access.infrastructure.UserAccount;
import com.cidas.edudoc.access.infrastructure.UserAccountRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 本地兼容认证：校验 BCrypt 口令与账号状态，SSO 账号不在此校验。 */
@Component
public class LocalAuthProvider implements AuthProvider {

    private static final String SOURCE_LOCAL = "LOCAL";
    private static final String STATUS_ACTIVE = "A";

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    public LocalAuthProvider(UserAccountRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Optional<UserPrincipal> authenticate(String username, String password) {
        Optional<UserAccount> found = repository.findByUsername(username);
        if (found.isEmpty() || !SOURCE_LOCAL.equals(found.get().authSource())) {
            return Optional.empty();
        }
        UserAccount account = found.get();
        if (account.passwordHash() == null
                || !passwordEncoder.matches(password, account.passwordHash())) {
            return Optional.empty();
        }
        if (!STATUS_ACTIVE.equals(account.accountStatus())) {
            throw new CidasBusinessException(ErrorCode.UNAUTHORIZED, "账号受限，请联系管理员");
        }
        return Optional.of(repository.loadPrincipal(account.id(), account.username()));
    }
}
