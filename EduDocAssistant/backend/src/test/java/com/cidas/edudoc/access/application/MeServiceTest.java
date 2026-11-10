package com.cidas.edudoc.access.application;

import com.cidas.edudoc.access.application.MeService.LoginRecordView;
import com.cidas.edudoc.access.application.MeService.PasswordChange;
import com.cidas.edudoc.access.application.MeService.ProfileView;
import com.cidas.edudoc.access.application.MeService.SecurityUpdate;
import com.cidas.edudoc.access.application.MeService.SecurityView;
import com.cidas.edudoc.access.application.MeService.SessionView;
import com.cidas.edudoc.access.application.impl.MeServiceImpl;
import com.cidas.edudoc.access.domain.RoleCode;
import com.cidas.edudoc.access.infrastructure.AuditLogRepository;
import com.cidas.edudoc.access.infrastructure.Course;
import com.cidas.edudoc.access.infrastructure.CourseRepository;
import com.cidas.edudoc.access.infrastructure.MeRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.JwtService;
import com.cidas.edudoc.common.security.TokenRevocationStore;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 个人中心与安全项单测（06 切片 2，纯 Mockito，不起 Spring 容器）。 */
class MeServiceTest {

    private static final String USER_ID = "0123456789ABCDEF0123456789ABCDEF";
    private static final String CURRENT_SESSION = "FEDCBA9876543210FEDCBA9876543210";
    private static final String OTHER_SESSION = "AAAAAAAABBBBBBBBCCCCCCCCDDDDDDDD";
    private static final Instant TOKEN_EXP = Instant.now().plusSeconds(120);

    private final MeRepository meRepository = mock(MeRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final TokenRevocationStore tokenRevocationStore = mock(TokenRevocationStore.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final MeServiceImpl service = new MeServiceImpl(meRepository, courseRepository,
            auditLogRepository, passwordEncoder, jwtService, tokenRevocationStore,
            new TransactionTemplate(txManager), new ObjectMapper());

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    private static void setPrincipal(String... roles) {
        AuthContext.set(new UserPrincipal(USER_ID, "student001", "张同学", Set.of(roles), Set.of(), Set.of()));
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    /** 本地账号全行；remark/passwordHash 等可按需覆盖。 */
    private static Map<String, Object> accountRow(Object... overrides) {
        Map<String, Object> base = row("username", "student001", "displayName", "张同学",
                "email", "zhang@university.edu.cn", "department", "计算机学院",
                "authSource", "LOCAL", "remark", null, "mfaEnabled", "N", "passwordHash", "hash");
        base.putAll(row(overrides));
        return base;
    }

    private static Map<String, Object> authRow(String id, String resultCode, String ip, String ua) {
        return row("id", id, "time", "2026-11-09T08:12:00", "resultCode", resultCode,
                "sourceIp", ip, "userAgent", ua);
    }

    // ===== 安全项 =====

    @Test
    void opsCannotDisableTwoFactor() {
        setPrincipal(RoleCode.OPS);

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.updateSecurity(new SecurityUpdate(false, null)));

        assertEquals(ErrorCode.FORBIDDEN, e.errorCode());
        verify(meRepository, never()).updateSecurity(any(), any(), any());
    }

    @Test
    void studentCanToggleTwoFactorAndKeepsRowValues() {
        setPrincipal(RoleCode.STUDENT);
        when(meRepository.findAccountById(USER_ID)).thenReturn(accountRow());

        service.updateSecurity(new SecurityUpdate(true, null));

        // twoFactor=true → mfa_enabled 写 Y；未带的字段保持原值
        verify(meRepository).updateSecurity(USER_ID, "Y", null);
    }

    @Test
    void securityDefaultsWhenNoColumnsExist() {
        when(meRepository.findAccountById(USER_ID)).thenReturn(accountRow());
        when(meRepository.lastPasswordChangeAt(USER_ID)).thenReturn(null);

        setPrincipal(RoleCode.STUDENT);
        SecurityView student = service.security();
        assertFalse(student.twoFactor());
        assertTrue(student.newDeviceAlert());   // 无对应列：策略默认开启（门面口径）
        assertNull(student.passwordUpdated());  // 无 PASSWORD_CHANGE 审计 → null
        assertFalse(student.sensitiveVerify());

        setPrincipal(RoleCode.OPS);
        assertTrue(service.security().sensitiveVerify());  // 策略默认：OPS 高危操作二次验证
    }

    // ===== 资料 =====

    @Test
    void profileUpdateRejectsSyncedFields() {
        setPrincipal(RoleCode.STUDENT);
        Map<String, Object> withAccount = new HashMap<>();
        withAccount.put("name", "李同学");
        withAccount.put("account", "2023001");
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(CidasBusinessException.class,
                () -> service.updateProfile(withAccount)).errorCode());

        Map<String, Object> withRole = new HashMap<>();
        withRole.put("role", "TEACHER");
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(CidasBusinessException.class,
                () -> service.updateProfile(withRole)).errorCode());

        verify(meRepository, never()).updateProfile(any(), any(), any(), any(), any());
    }

    @Test
    void profileViewShapeAndPhonePersistViaRemarkJson() {
        setPrincipal(RoleCode.STUDENT);
        when(courseRepository.listByUser(USER_ID)).thenReturn(List.of(
                new Course("C1", "CS101", "Web开发技术", "2026-FALL", "STUDENT"),
                new Course("C2", "CS102", "编译原理", "2026-FALL", "TEACHER")));
        // 第一次读原行做合并，第二次（updateProfile 返回前重读）返回已带 remark JSON 的行
        when(meRepository.findAccountById(USER_ID)).thenReturn(
                accountRow(), accountRow("remark", "{\"phone\":\"13800001111\"}"));

        ProfileView view = service.updateProfile(Map.of("phone", "13800001111"));

        ArgumentCaptor<String> remark = ArgumentCaptor.forClass(String.class);
        verify(meRepository).updateProfile(eq(USER_ID), eq("张同学"), eq("计算机学院"),
                eq("zhang@university.edu.cn"), remark.capture());
        assertTrue(remark.getValue().contains("13800001111"));  // phone 落 remark JSON（不加 DDL 的取舍）
        assertEquals("13800001111", view.phone());
        assertEquals("student001", view.account());
        assertEquals("学生账号", view.roleLabel());
        assertEquals(RoleCode.STUDENT, view.role());
        // 学生 extra 只列选课（memberRole=STUDENT），授课行不混入
        assertEquals("选课课程", view.extra().get(0).label());
        assertEquals("《Web开发技术》", view.extra().get(0).value());
    }

    // ===== 改密 =====

    @Test
    void wrongOldPasswordIsBadRequestNot401() {
        setPrincipal(RoleCode.STUDENT);
        when(meRepository.findAccountById(USER_ID)).thenReturn(accountRow());
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.changePassword(new PasswordChange("bad", "NewPass456", "tok")));

        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());  // 401 系会被前端拦截器硬跳登录，改密参数错不属未认证
        verify(meRepository, never()).updatePasswordHash(any(), any());
        verifyNoInteractions(tokenRevocationStore);
    }

    @Test
    void weakNewPasswordAndSsoAccountRejected() {
        setPrincipal(RoleCode.STUDENT);
        when(meRepository.findAccountById(USER_ID)).thenReturn(accountRow());
        when(passwordEncoder.matches("OldPass123", "hash")).thenReturn(true);
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(CidasBusinessException.class,
                () -> service.changePassword(new PasswordChange("OldPass123", "abcdefgh", "tok"))).errorCode());

        // SSO 账号无本地密码哈希 → 40001 引导走学校门户
        when(meRepository.findAccountById(USER_ID))
                .thenReturn(accountRow("authSource", "SSO", "passwordHash", null));
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(CidasBusinessException.class,
                () -> service.changePassword(new PasswordChange("OldPass123", "NewPass456", "tok"))).errorCode());
        verify(meRepository, never()).updatePasswordHash(any(), any());
    }

    @Test
    void passwordChangeAuditsAndRevokesCurrentToken() {
        setPrincipal(RoleCode.STUDENT);
        when(meRepository.findAccountById(USER_ID)).thenReturn(accountRow());
        when(passwordEncoder.matches("OldPass123", "hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass456")).thenReturn("newhash");
        when(jwtService.parse("tok")).thenReturn(new JwtService.TokenClaims(
                USER_ID, "student001", Set.of("STUDENT"), "jti-1", TOKEN_EXP));

        service.changePassword(new PasswordChange("OldPass123", "NewPass456", "tok"));

        verify(meRepository).updatePasswordHash(USER_ID, "newhash");
        verify(auditLogRepository).record(eq("PASSWORD_CHANGE"), eq("ACCOUNT"), eq("student001"),
                eq(USER_ID), eq("STUDENT"), isNull(), eq("SUCCESS"), isNull());
        verify(txManager).commit(any());
        // 取舍：撤销表无用户→jti 索引，「他处令牌」不可枚举；现撤销当前令牌强制重登，
        // 他处令牌在 TTL（≤30 分钟）内自然过期（见 MeServiceImpl 类注释，已进待确认清单）
        verify(tokenRevocationStore).revoke("jti-1", TOKEN_EXP);
    }

    // ===== 会话 =====

    @Test
    void currentSessionCannotBeEnded() {
        setPrincipal(RoleCode.STUDENT);
        when(meRepository.listAuthLogins(eq(USER_ID), any(), anyInt())).thenReturn(List.of(
                authRow(CURRENT_SESSION, "SUCCESS", "10.20.31.7", "Chrome"),
                authRow(OTHER_SESSION, "SUCCESS", "10.20.44.9", null)));
        when(meRepository.findAuthLoginById(USER_ID, CURRENT_SESSION))
                .thenReturn(authRow(CURRENT_SESSION, "SUCCESS", "10.20.31.7", "Chrome"));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.endSession(CURRENT_SESSION));

        assertEquals(ErrorCode.CONFLICT, e.errorCode());
        assertTrue(e.getMessage().contains("当前会话"));
    }

    @Test
    void otherSessionEndIsConflictWithoutTokenIndex() {
        setPrincipal(RoleCode.STUDENT);
        when(meRepository.listAuthLogins(eq(USER_ID), any(), anyInt())).thenReturn(List.of(
                authRow(CURRENT_SESSION, "SUCCESS", "10.20.31.7", "Chrome")));
        when(meRepository.findAuthLoginById(USER_ID, OTHER_SESSION))
                .thenReturn(authRow(OTHER_SESSION, "SUCCESS", "10.20.44.9", null));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.endSession(OTHER_SESSION));

        assertEquals(ErrorCode.CONFLICT, e.errorCode());  // 审计无 jti，无法精确撤销 → 后续项
        assertTrue(e.getMessage().contains("令牌会话索引"));
    }

    @Test
    void endSessionRejectsBadIdAndForeignOrMissingRow() {
        setPrincipal(RoleCode.STUDENT);
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(CidasBusinessException.class,
                () -> service.endSession("xyz")).errorCode());  // Ids.isHex16 前置，防 HEXTORAW 库异常
        when(meRepository.findAuthLoginById(USER_ID, OTHER_SESSION)).thenReturn(null);
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(CidasBusinessException.class,
                () -> service.endSession(OTHER_SESSION)).errorCode());
    }

    @Test
    void sessionsDeriveFromAuthSuccessAndMarkLatestCurrent() {
        setPrincipal(RoleCode.STUDENT);
        when(meRepository.listAuthLogins(eq(USER_ID), any(), anyInt())).thenReturn(List.of(
                authRow(CURRENT_SESSION, "SUCCESS", "10.20.31.7", "Windows · Chrome"),
                authRow(OTHER_SESSION, "40101", null, null)));  // 失败登录不进会话列表

        List<SessionView> sessions = service.sessions();

        assertEquals(1, sessions.size());
        assertEquals(CURRENT_SESSION, sessions.get(0).id());
        assertTrue(sessions.get(0).current());
        assertEquals("Windows · Chrome", sessions.get(0).device());
    }

    // ===== 登录记录 =====

    @Test
    void invalidLoginRecordRangeRejected() {
        setPrincipal(RoleCode.STUDENT);

        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(CidasBusinessException.class,
                () -> service.loginRecords("90d")).errorCode());
        verifyNoInteractions(meRepository);
    }

    @Test
    void loginRecordsDeriveResultLocationAndRemote() {
        setPrincipal(RoleCode.STUDENT);
        // 降序三行：同网段相邻 → 非异地；跨网段 → 异地；窗口内最早一条无前序 → 非异地
        when(meRepository.listAuthLogins(eq(USER_ID), any(), anyInt())).thenReturn(List.of(
                authRow("S1", "SUCCESS", "10.20.31.7", "Windows · Chrome"),
                authRow("S2", "SUCCESS", "10.20.44.9", null),
                authRow("S3", "40101", "183.12.8.46", "Android")));

        List<LoginRecordView> records = service.loginRecords("24h");

        assertEquals(3, records.size());
        assertEquals("SUCCESS", records.get(0).result());
        assertEquals("校内网络", records.get(0).location());  // 私网段近似校内
        assertFalse(records.get(0).remote());               // 10.20 == 10.20
        assertEquals("未知设备", records.get(1).device());   // 现 AUTH 审计不写 user_agent
        assertTrue(records.get(1).remote());                // 10.20 != 183.12
        assertEquals("BLOCKED", records.get(2).result());   // 非 SUCCESS 一律 BLOCKED
        assertEquals("校外网络", records.get(2).location());
        assertFalse(records.get(2).remote());               // 无更早记录可比
    }
}
