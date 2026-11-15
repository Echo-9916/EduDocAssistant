package com.cidas.edudoc.access.application;

import com.cidas.edudoc.common.api.PageResult;

/** 运维用户目录与账号冻结/解冻（只读列表 + 两种状态迁移）。 */
public interface OpsUserService {

    PageResult<UserView> list(Query query);

    /** 冻结：A → L；禁止冻结自己与 OPS 账号。 */
    void freeze(String userId);

    /** 解冻：L → A。 */
    void unfreeze(String userId);

    record Query(String keyword, String role, String status, Integer page, Integer pageSize) {
    }

    record UserView(String id, String username, String displayName, String role,
                    String accountStatus, String department, String lastLoginTime) {
    }
}