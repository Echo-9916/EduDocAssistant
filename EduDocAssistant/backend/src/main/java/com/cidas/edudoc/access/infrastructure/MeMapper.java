package com.cidas.edudoc.access.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 个人中心查询与单表更新（06 切片 2 新增；SQL 见 resources/mapper/MeMapper.xml）。 */
@Mapper
public interface MeMapper {

    /** 本人资料/安全/改密所需列（含 password_hash 与 remark，仅本人链路调用）。 */
    Map<String, Object> selectProfileById(@Param("userIdHex") String userIdHex);

    int updateProfile(@Param("userIdHex") String userIdHex, @Param("displayName") String displayName,
                      @Param("department") String department, @Param("email") String email,
                      @Param("remark") String remark);

    int updateSecurity(@Param("userIdHex") String userIdHex, @Param("mfaEnabled") String mfaEnabled,
                       @Param("remark") String remark);

    int updatePasswordHash(@Param("userIdHex") String userIdHex, @Param("passwordHash") String passwordHash);

    /** 最近一次成功改密（PASSWORD_CHANGE 审计）时间，无记录返回 null。 */
    String selectLastPasswordChangeAt(@Param("userIdHex") String userIdHex);

    /** 窗口内本人 AUTH 审计行（时间降序），登录记录与会话列表共用。 */
    List<Map<String, Object>> selectAuthLogins(@Param("userIdHex") String userIdHex,
                                               @Param("since") OffsetDateTime since,
                                               @Param("limit") int limit);

    /** 单条 AUTH 审计行，actor_id 限定本人（sessionId 调用前已过 Ids.isHex16）。 */
    Map<String, Object> selectAuthLoginById(@Param("userIdHex") String userIdHex,
                                            @Param("sessionId") String sessionId);
}
