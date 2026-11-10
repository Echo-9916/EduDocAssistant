package com.cidas.edudoc.access.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 个人中心持久层门面（06 切片 2）：应用层不直连 Mapper，SQL 委托给 {@link MeMapper}。 */
@Repository
@RequiredArgsConstructor
public class MeRepository {

    private final MeMapper mapper;

    public Map<String, Object> findAccountById(String userIdHex) {
        return mapper.selectProfileById(userIdHex);
    }

    public void updateProfile(String userIdHex, String displayName, String department, String email,
                              String remark) {
        mapper.updateProfile(userIdHex, displayName, department, email, remark);
    }

    public void updateSecurity(String userIdHex, String mfaEnabled, String remark) {
        mapper.updateSecurity(userIdHex, mfaEnabled, remark);
    }

    public void updatePasswordHash(String userIdHex, String passwordHash) {
        mapper.updatePasswordHash(userIdHex, passwordHash);
    }

    /** 最近一次成功改密（PASSWORD_CHANGE 审计）时间，无记录返回 null。 */
    public String lastPasswordChangeAt(String userIdHex) {
        return mapper.selectLastPasswordChangeAt(userIdHex);
    }

    /** 窗口内本人 AUTH 审计行（时间降序），登录记录与会话列表共用。 */
    public List<Map<String, Object>> listAuthLogins(String userIdHex, OffsetDateTime since, int limit) {
        return mapper.selectAuthLogins(userIdHex, since, limit);
    }

    /** 单条 AUTH 审计行（actor_id 限定本人），不存在或非本人返回 null。 */
    public Map<String, Object> findAuthLoginById(String userIdHex, String sessionId) {
        return mapper.selectAuthLoginById(userIdHex, sessionId);
    }
}
