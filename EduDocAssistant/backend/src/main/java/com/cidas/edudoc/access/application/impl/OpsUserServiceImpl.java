package com.cidas.edudoc.access.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.access.application.OpsUserService;
import com.cidas.edudoc.access.domain.AccountStatus;
import com.cidas.edudoc.access.infrastructure.OpsUserMapper;
import com.cidas.edudoc.access.infrastructure.OpsUserRow;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 运维用户目录：SQL 在 OpsUserMapper.xml，冻结/解冻与审计同一事务。 */
@Service
@RequiredArgsConstructor
public class OpsUserServiceImpl implements OpsUserService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> STATUSES = Set.of("A", "I", "L", "D");
    private static final Set<String> ROLES = Set.of("STUDENT", "TEACHER", "OPS");

    private final OpsUserMapper mapper;
    private final AuditService auditService;

    @Override
    public PageResult<UserView> list(Query query) {
        String status = blankToNull(query.status());
        if (status != null && !STATUSES.contains(status)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "status 仅支持 A、I、L、D");
        }
        String role = blankToNull(query.role());
        if (role != null && !ROLES.contains(role)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "role 仅支持 STUDENT、TEACHER、OPS");
        }
        String keyword = escapeLike(query.keyword());
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int pageSize = query.pageSize() == null || query.pageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.pageSize(), MAX_PAGE_SIZE);
        long offset = (long) (page - 1) * pageSize;
        long total = mapper.count(keyword, role, status);
        List<UserView> items = total == 0 ? List.of()
                : mapper.selectPage(keyword, role, status, offset, pageSize).stream()
                        .map(OpsUserServiceImpl::toView).toList();
        return new PageResult<>(items, total, page, pageSize);
    }

    @Override
    @Transactional
    public void freeze(String userId) {
        changeStatus(userId, AccountStatus.ACTIVE.code(), AccountStatus.LOCKED.code(), "ACCOUNT_FROZEN",
                "仅正常状态账号可冻结（当前已冻结或非正常状态）");
    }

    @Override
    @Transactional
    public void unfreeze(String userId) {
        changeStatus(userId, AccountStatus.LOCKED.code(), AccountStatus.ACTIVE.code(), "ACCOUNT_UNFROZEN",
                "账号未处于冻结状态");
    }

    private void changeStatus(String userId, String expected, String next, String action,
                              String mismatchMessage) {
        if (!Ids.isHex16(userId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "用户 ID 格式不合法");
        }
        String currentUserId = AuthContext.require().userId();
        if (currentUserId.equalsIgnoreCase(userId)) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "不能操作当前登录账号");
        }
        OpsUserRow row = mapper.selectById(userId);
        if (row == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (mapper.countRole(userId, "OPS") > 0) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "不能冻结或解冻运维账号");
        }
        String before = row.getAccountStatus();
        if (!expected.equals(before)) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, mismatchMessage);
        }
        if (mapper.updateStatus(userId, expected, next) == 0) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "账号状态已变化，请刷新后重试");
        }
        auditService.record(action, "ACCOUNT", row.getUsername(), null, "200",
                Map.of("userId", userId, "prevStatus", before, "newStatus", next));
    }

    private static UserView toView(OpsUserRow row) {
        return new UserView(row.getId(), row.getUsername(), row.getDisplayName(), row.getRole(),
                row.getAccountStatus(), row.getDepartment(), row.getLastLoginTime());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String escapeLike(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}