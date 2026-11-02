package com.cidas.edudoc.access.application.impl;

import com.cidas.edudoc.access.application.AuthProvider;
import com.cidas.edudoc.access.domain.AccountStatus;
import com.cidas.edudoc.access.domain.AuthSource;
import com.cidas.edudoc.access.infrastructure.UserAccount;
import com.cidas.edudoc.access.infrastructure.UserAccountRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.UserPrincipal;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/** 本地兼容认证：校验 BCrypt 口令与账号状态，SSO 账号不在此校验。 */
@Component
@RequiredArgsConstructor
public class LocalAuthProvider implements AuthProvider {

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Optional<UserPrincipal> authenticate(String username, String password) {
        Optional<UserAccount> account = repository.findByUsername(username)
                .filter(a -> AuthSource.LOCAL.code().equals(a.getAuthSource()))
                .filter(a -> a.getPasswordHash() != null
                        && passwordEncoder.matches(password, a.getPasswordHash()));
        if (account.isEmpty()) {
            return Optional.empty();
        }
        UserAccount found = account.get();
        if (!AccountStatus.ACTIVE.code().equals(found.getAccountStatus())) {
            // 口令已校验通过，账号存在性已暴露；data.accountStatus 为稳定契约（P2-2），中文文案仅用于展示
            throw new CidasBusinessException(ErrorCode.UNAUTHORIZED,
                    "账号受限：" + restrictedReason(found.getAccountStatus()), false,
                    Map.of("accountStatus", found.getAccountStatus()));
        }
        UserPrincipal principal = repository.loadPrincipal(found.getId(), found.getUsername());
        if (principal == null) {
            // P2-S1：两次查询之间账号状态并发变化（锁定/注销/删除），稳定返回 40101 而非 NPE→50001
            throw new CidasBusinessException(ErrorCode.UNAUTHORIZED, "账号状态已变更，请重新登录");
        }
        return Optional.of(principal);
    }

    /** 账号状态 → 受限原因文案（对齐 edudoc_user.account_status 的 L/I/D 语义）。 */
    private static String restrictedReason(String status) {
        if (AccountStatus.LOCKED.code().equals(status)) {
            return "账号已锁定，请联系管理员";
        }
        if (AccountStatus.INACTIVE.code().equals(status)) {
            return "账号未激活，请先完成激活";
        }
        if (AccountStatus.DISABLED.code().equals(status)) {
            return "账号已注销，无法登录";
        }
        return "账号状态异常，请联系管理员";
    }
}
