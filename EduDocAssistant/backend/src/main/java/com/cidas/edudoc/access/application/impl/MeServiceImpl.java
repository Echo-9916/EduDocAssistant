package com.cidas.edudoc.access.application.impl;

import com.cidas.edudoc.access.application.MeService;
import com.cidas.edudoc.access.domain.AuthSource;
import com.cidas.edudoc.access.domain.RoleCode;
import com.cidas.edudoc.access.infrastructure.AuditLogRepository;
import com.cidas.edudoc.access.infrastructure.CourseRepository;
import com.cidas.edudoc.access.infrastructure.MeRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.JwtService;
import com.cidas.edudoc.common.security.RequestMeta;
import com.cidas.edudoc.common.security.TokenRevocationStore;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 个人中心与安全项实现（06 切片 2 · 任务 2-6）：SQL 全部在 MeMapper.xml，这里只做校验与装配。
 *
 * <p>不加 DDL 的三处数据取舍（同步进 docs/tasks/06-待确认清单.md）：
 * <ol>
 * <li>phone 与 newDeviceAlert 无对应列，落 edudoc_user.remark 的小 JSON
 *     （{"phone":"…","newDeviceAlert":true}）。remark 本义「管理备注」，当前种子与端点均未使用，
 *     借用无冲突；历史非 JSON 备注按空对象处理。日后若做管理备注功能应拆独立列。</li>
 * <li>passwordUpdated 无对应列，以 PASSWORD_CHANGE 审计时间近似，上线前的改密不可追溯 → null。</li>
 * <li>TokenRevocationStore 是纯 jti 黑名单（无用户→jti 索引，且在 common/** 禁改），无法枚举/撤销
 *     他处令牌：会话列表退化为审计 AUTH 成功记录派生的「登录设备列表」，DELETE 一律 40901；
 *     改密只撤销当前令牌强制重登（设计文档「返回需在登录页重登」），他处令牌在 TTL（30 分钟）内自然过期。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class MeServiceImpl implements MeService {

    private static final String DEFAULT_RANGE = "24h";
    private static final Map<String, Integer> RANGE_HOURS = Map.of("24h", 24, "7d", 7 * 24, "30d", 30 * 24);
    private static final int RECORD_LIMIT = 200;

    // 会话窗口固定 7 天：令牌 TTL 仅 30 分钟，「会话」实为登录痕迹近似，窗口再大只是噪音
    private static final int SESSION_WINDOW_HOURS = 7 * 24;
    private static final int SESSION_LIMIT = 20;

    /** PUT /profile 白名单：其余键（account/role/课程等同步字段）一律 40001（前端置灰，后端兜底）。 */
    private static final Set<String> EDITABLE_PROFILE_KEYS = Set.of("name", "org", "email", "phone");

    private final MeRepository meRepository;
    private final CourseRepository courseRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenRevocationStore tokenRevocationStore;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    // ===== 资料 =====

    @Override
    public ProfileView profile() {
        UserPrincipal principal = AuthContext.require();
        Map<String, Object> row = requireAccount(principal.userId());
        String role = primaryRole(principal.roles());
        return new ProfileView(str(row, "displayName"), str(row, "username"), roleLabel(role),
                str(row, "department"), str(row, "email"), phoneOf(str(row, "remark")), role,
                extraOf(role, principal.userId(), str(row, "department"), str(row, "authSource")));
    }

    @Override
    public ProfileView updateProfile(Map<String, Object> patch) {
        UserPrincipal principal = AuthContext.require();
        if (patch == null || patch.keySet().stream().anyMatch(k -> !EDITABLE_PROFILE_KEYS.contains(k))) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST,
                    "仅可修改 name、org、email、phone；学号/工号、角色与课程为同步字段，不允许修改");
        }
        // 字段规则：键缺席或 null → 保持原值；空串 → 清空（name 例外，40001）；超列宽 40001（防 ORA 截断异常）
        String name = textField(patch, "name", 100, true);
        String org = textField(patch, "org", 200, false);
        String email = textField(patch, "email", 200, false);
        String phone = textField(patch, "phone", 20, false);
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+$")) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "邮箱格式不正确");
        }
        Map<String, Object> row = requireAccount(principal.userId());
        ObjectNode remark = remarkNode(str(row, "remark"));
        if (patch.get("phone") != null) {
            if (phone == null) {
                remark.remove("phone");
            } else {
                remark.put("phone", phone);
            }
        }
        // 合并规则：JSON null/键缺席 → 保持原值；空串 → 清空（name 除外，40001）
        meRepository.updateProfile(principal.userId(),
                name != null ? name : str(row, "displayName"),
                patch.get("org") != null ? org : str(row, "department"),
                patch.get("email") != null ? email : str(row, "email"),
                remark.isEmpty() ? null : remark.toString());
        return profile();
    }

    /** 只读 extra：学生=选课课程、教师=授课课程、运维=值班组(department)+账号来源(auth_source)。 */
    private List<ExtraField> extraOf(String role, String userIdHex, String department, String authSource) {
        if (RoleCode.OPS.equals(role)) {
            return List.of(
                    new ExtraField("值班组", department != null && !department.isBlank() ? department : "未分配"),
                    new ExtraField("账号来源", AuthSource.LOCAL.code().equals(authSource) ? "本地账号" : "统一身份认证"));
        }
        boolean student = RoleCode.STUDENT.equals(role);
        String names = courseRepository.listByUser(userIdHex).stream()
                .filter(c -> student == "STUDENT".equals(c.getMemberRole()))
                .map(c -> "《" + c.getCourseName() + "》")
                .collect(Collectors.joining());
        return List.of(new ExtraField(student ? "选课课程" : "授课课程", names.isEmpty() ? "暂无" : names));
    }

    // ===== 安全项 =====

    @Override
    public SecurityView security() {
        UserPrincipal principal = AuthContext.require();
        Map<String, Object> row = requireAccount(principal.userId());
        // newDeviceAlert 无对应列：策略默认开启（05 期门面三端 mock 均默认 true）；持久化在 remark JSON
        boolean newDeviceAlert = remarkNode(str(row, "remark")).path("newDeviceAlert").asBoolean(true);
        // sensitiveVerify 无对应列且只读：策略默认 OPS 开、其余关（门面口径「运维特有：高危操作二次验证」）
        boolean sensitiveVerify = principal.roles().contains(RoleCode.OPS);
        return new SecurityView("Y".equals(str(row, "mfaEnabled")), newDeviceAlert,
                meRepository.lastPasswordChangeAt(principal.userId()), sensitiveVerify);
    }

    @Override
    public SecurityView updateSecurity(SecurityUpdate update) {
        UserPrincipal principal = AuthContext.require();
        if (Boolean.FALSE.equals(update.twoFactor()) && principal.roles().contains(RoleCode.OPS)) {
            throw new CidasBusinessException(ErrorCode.FORBIDDEN, "运维角色不允许关闭双因素认证");
        }
        Map<String, Object> row = requireAccount(principal.userId());
        String mfa = update.twoFactor() == null ? str(row, "mfaEnabled") : (update.twoFactor() ? "Y" : "N");
        ObjectNode remark = remarkNode(str(row, "remark"));
        if (update.newDeviceAlert() != null) {
            remark.put("newDeviceAlert", update.newDeviceAlert());
        }
        meRepository.updateSecurity(principal.userId(), mfa, remark.isEmpty() ? null : remark.toString());
        return security();
    }

    // ===== 改密 =====

    @Override
    public void changePassword(PasswordChange command) {
        UserPrincipal principal = AuthContext.require();
        if (isBlank(command.oldPassword()) || isBlank(command.newPassword())) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "原密码与新密码不能为空");
        }
        Map<String, Object> row = requireAccount(principal.userId());
        if (!AuthSource.LOCAL.code().equals(str(row, "authSource")) || str(row, "passwordHash") == null) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "统一身份认证账号不支持本地改密，请通过学校门户修改");
        }
        // 旧密码错误用 40001，不用 401 系：前端 http 拦截器对 40101/40102 一律硬跳 /login，
        // 用户打错一次原密码就会被踢出登录且看不到「原密码不正确」。本请求本身已认证，
        // 参数不对属 400 族；登录接口的 40101 才是「未认证」语义。
        if (!passwordEncoder.matches(command.oldPassword(), str(row, "passwordHash"))) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "原密码不正确");
        }
        if (!isStrongEnough(command.newPassword())) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "新密码至少 8 位，且需同时包含字母和数字");
        }
        String encoded = passwordEncoder.encode(command.newPassword());
        transactionTemplate.executeWithoutResult(status -> {
            meRepository.updatePasswordHash(principal.userId(), encoded);
            auditLogRepository.record("PASSWORD_CHANGE", "ACCOUNT", str(row, "username"),
                    principal.userId(), primaryRole(principal.roles()), null, "SUCCESS", null,
                    RequestMeta.clientIp(), RequestMeta.userAgent());
        });
        // 撤销范围取舍：撤销表无用户→jti 索引，「其余令牌」不可枚举；现撤销当前令牌强制重登
        //（设计文档「返回需在登录页重登」），他处令牌在 TTL（≤30 分钟）内自然过期。
        // 要即时全撤需给 TokenRevocationStore 加按用户索引（common/**，需批准 → 待确认清单）。
        JwtService.TokenClaims claims = jwtService.parse(command.bearerToken());
        tokenRevocationStore.revoke(claims.jti(), claims.expiresAt());
    }

    /** 强度最低口径：≥8 位且含字母与数字（最小可用；分级强度属前端后续项）。 */
    private static boolean isStrongEnough(String password) {
        return password.length() >= 8
                && password.chars().anyMatch(Character::isLetter)
                && password.chars().anyMatch(Character::isDigit);
    }

    // ===== 会话 =====

    @Override
    public List<SessionView> sessions() {
        UserPrincipal principal = AuthContext.require();
        List<Map<String, Object>> rows = meRepository
                .listAuthLogins(principal.userId(), OffsetDateTime.now().minusHours(SESSION_WINDOW_HOURS), SESSION_LIMIT)
                .stream().filter(r -> "SUCCESS".equals(str(r, "resultCode"))).toList();
        // current = 最近一条成功登录（多端并发时无法把审计行精确对到当前令牌，启发式口径）
        List<SessionView> list = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            list.add(new SessionView(str(r, "id"), deviceOf(str(r, "userAgent")), str(r, "sourceIp"),
                    str(r, "time"), i == 0));
        }
        return list;
    }

    @Override
    public void endSession(String sessionId) {
        UserPrincipal principal = AuthContext.require();
        if (!Ids.isHex16(sessionId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "会话 ID 格式不合法");
        }
        if (meRepository.findAuthLoginById(principal.userId(), sessionId) == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        List<SessionView> sessions = sessions();
        if (!sessions.isEmpty() && sessions.get(0).id().equals(sessionId)) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "当前会话不可下线");
        }
        // 精确下线需把审计行映射到令牌 jti：AUTH 审计未记录 jti 且撤销表无用户索引 → 无法撤销特定会话。
        // 会话精确下线依赖令牌会话索引，属后续项（待确认清单）；成功写审计的路径届时一并补。
        throw new CidasBusinessException(ErrorCode.CONFLICT,
                "该会话暂不支持单独下线：精确下线依赖令牌会话索引（后续项），令牌将在有效期（30 分钟）内自然过期");
    }

    // ===== 登录记录 =====

    @Override
    public List<LoginRecordView> loginRecords(String range) {
        UserPrincipal principal = AuthContext.require();
        String normalized = range == null || range.isBlank() ? DEFAULT_RANGE : range.trim();
        Integer hours = RANGE_HOURS.get(normalized);
        if (hours == null) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "range 仅支持 24h、7d、30d");
        }
        List<Map<String, Object>> rows = meRepository.listAuthLogins(principal.userId(),
                OffsetDateTime.now().minusHours(hours), RECORD_LIMIT);
        // 行按时间降序；异地 = 与上一条（更早记录）IP 前两段不同，现算。
        // AUTH 审计自 2026-11-13 起写 source_ip；历史行 IP 为空时该条恒 false，窗口内最早一条看不到窗外前一条，恒 false。
        List<LoginRecordView> list = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            String ip = str(row, "sourceIp");
            String olderIp = i + 1 < rows.size() ? str(rows.get(i + 1), "sourceIp") : null;
            boolean remote = ipPrefix(ip) != null && ipPrefix(olderIp) != null
                    && !ipPrefix(ip).equals(ipPrefix(olderIp));
            list.add(new LoginRecordView(str(row, "time"), deviceOf(str(row, "userAgent")), ip,
                    locationOf(ip), "SUCCESS".equals(str(row, "resultCode")) ? "SUCCESS" : "BLOCKED", remote));
        }
        return list;
    }

    // ===== 公共小件 =====

    private Map<String, Object> requireAccount(String userIdHex) {
        Map<String, Object> row = meRepository.findAccountById(userIdHex);
        if (row == null) {
            // 令牌有效但账号已删/并发注销：沿用登录链路 P2-S1 口径 40101
            throw new CidasBusinessException(ErrorCode.UNAUTHORIZED, "账号不存在或状态已变更，请重新登录");
        }
        return row;
    }

    /** 多角色取主角色：OPS > TEACHER > STUDENT（个人中心单角色展示口径）。 */
    private static String primaryRole(Set<String> roles) {
        if (roles.contains(RoleCode.OPS)) {
            return RoleCode.OPS;
        }
        if (roles.contains(RoleCode.TEACHER)) {
            return RoleCode.TEACHER;
        }
        if (roles.contains(RoleCode.STUDENT)) {
            return RoleCode.STUDENT;
        }
        return roles.stream().sorted().findFirst().orElse(null);
    }

    /** 角色中文标签，与门面 mock（profile.ts roleLabel）对齐；自定义角色直接回显编码。 */
    private static String roleLabel(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case RoleCode.STUDENT -> "学生账号";
            case RoleCode.TEACHER -> "教师账号";
            case RoleCode.OPS -> "运维管理员";
            default -> role;
        };
    }

    /** 白名单字段取值：键缺席或 null → null（保持原值）；空串 → null（清空，rejectBlank 时 40001）；超列宽 40001。 */
    private static String textField(Map<String, Object> patch, String key, int maxLength, boolean rejectBlank) {
        Object raw = patch.get(key);
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        if (value.isEmpty()) {
            if (rejectBlank) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, key + " 不能为空");
            }
            return null;
        }
        if (value.length() > maxLength) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, key + " 长度不能超过 " + maxLength);
        }
        return value;
    }

    /** remark 扩展 JSON：非 JSON（历史管理备注纯文本）按空对象处理；见类注释取舍 1。 */
    private ObjectNode remarkNode(String remark) {
        if (remark == null || remark.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(remark);
            return node.isObject() ? (ObjectNode) node : JsonNodeFactory.instance.objectNode();
        } catch (Exception e) {
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private String phoneOf(String remark) {
        JsonNode phone = remarkNode(remark).get("phone");
        return phone == null || phone.isNull() ? null : phone.asText();
    }

    /** 设备名：AUTH 审计自 2026-11-13 起写 user_agent，历史行缺失时给「未知设备」。 */
    private static String deviceOf(String userAgent) {
        return userAgent == null || userAgent.isBlank() ? "未知设备" : userAgent;
    }

    /** 位置：无 IP 地理库，按私网网段近似「校内网络」，公网即「校外网络」；IP 缺失返回 null。 */
    private static String locationOf(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        return isCampusIp(ip) ? "校内网络" : "校外网络";
    }

    private static boolean isCampusIp(String ip) {
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("127.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    /** IP 前缀 = 前两段（如 10.20）；非点分格式退化为整串；缺失返回 null（异地判定恒 false）。 */
    private static String ipPrefix(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String[] parts = ip.trim().split("\\.");
        return parts.length >= 2 ? parts[0] + "." + parts[1] : ip.trim();
    }

    private static String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
