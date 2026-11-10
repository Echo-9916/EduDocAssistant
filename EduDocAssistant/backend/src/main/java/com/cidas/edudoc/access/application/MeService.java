package com.cidas.edudoc.access.application;

import java.util.List;
import java.util.Map;

/** 个人中心与安全项（06 切片 2 · 任务 2-6）：全部「本人」口径，身份取 AuthContext。 */
public interface MeService {

    /** 个人资料：account 学生=学号、教师/运维=工号（username）；extra 按角色给只读口径。 */
    ProfileView profile();

    /** 只接受 name/org/email/phone；出现学号/角色/课程等同步字段键 → 40001。 */
    ProfileView updateProfile(Map<String, Object> patch);

    /** 安全项：无对应列的项按策略默认值返回（见实现注释）。 */
    SecurityView security();

    /** 更新双因素/新设备提醒；OPS 关双因素 → 40301（应用层校验，不写触发器）。 */
    SecurityView updateSecurity(SecurityUpdate update);

    /** 改密：校验旧密码（40101）、强度（40001）；成功后写 PASSWORD_CHANGE 审计并撤销当前令牌强制重登。 */
    void changePassword(PasswordChange command);

    /** 会话列表：审计 AUTH 成功记录派生的「登录设备列表」，最近一条标 current。 */
    List<SessionView> sessions();

    /** 下线会话：当前会话 40901；非当前会话因无令牌会话索引同样 40901（见实现注释）。 */
    void endSession(String sessionId);

    /** 登录记录：range ∈ 24h/7d/30d（缺省 24h，非法 40001）；异地按与上一条 IP 前缀不同现算。 */
    List<LoginRecordView> loginRecords(String range);

    record ExtraField(String label, String value) {
    }

    record ProfileView(String name, String account, String roleLabel, String org, String email,
                       String phone, String role, List<ExtraField> extra) {
    }

    record SecurityView(boolean twoFactor, boolean newDeviceAlert, String passwordUpdated,
                        boolean sensitiveVerify) {
    }

    record SecurityUpdate(Boolean twoFactor, Boolean newDeviceAlert) {
    }

    /** bearerToken 为当前请求的裸令牌（Controller 剥掉 "Bearer " 前缀），用于改密后撤销当前会话。 */
    record PasswordChange(String oldPassword, String newPassword, String bearerToken) {
    }

    record SessionView(String id, String device, String ip, String loginTime, boolean current) {
    }

    record LoginRecordView(String time, String device, String ip, String location, String result,
                           boolean remote) {
    }
}
