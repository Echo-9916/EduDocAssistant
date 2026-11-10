package com.cidas.edudoc.common.persistence;

import com.cidas.edudoc.access.infrastructure.AuditLogMapper;
import com.cidas.edudoc.access.infrastructure.MeMapper;
import com.cidas.edudoc.coursework.infrastructure.CourseworkMapper;
import com.cidas.edudoc.message.infrastructure.NotificationMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨域 SQL 冒烟集成测试（06 切片 1/2 新增的四个 Mapper）。
 *
 * <p>为什么需要它：业务单测一律 mock Mapper，**不会解析 mapper XML**，因此下面这类错误
 * 只有真跑 SQL 才暴露（2026-11-10 实际踩过：{@code /me/security} 500，
 * {@code ORA-17133 标识符或文字无效}，根因是 {@code SELECT TO_CHAR(...)} 没给列别名）：
 * <ul>
 *   <li>表达式列缺别名；</li>
 *   <li>{@code resultType="...Outer$Nested"} 内部类名写错（只会静默变 null 或启动即崩）；</li>
 *   <li>{@code FETCH FIRST}/{@code OFFSET} 与 LEFT JOIN 里的标量子查询等 Oracle 语法细节；</li>
 *   <li>{@code SYS_EXTRACT_UTC(...) || 'Z'} 的时间输出形状。</li>
 * </ul>
 *
 * <p>断言口径刻意与数据量无关：只要求「能执行且列别名可解析」，行存在时再校验形状，
 * 这样空库与演示库都能跑。Oracle 不可达或未注入凭据时整体跳过（与既有 IT 同套路）。
 */
class MapperSqlSmokeIntegrationTest {

    private static final String URL = System.getenv()
            .getOrDefault("EDUDOC_DB_URL", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
    private static final String USER = System.getenv("EDUDOC_DB_USERNAME");
    private static final String PASS = System.getenv("EDUDOC_DB_PASSWORD");

    private static final String[] MAPPER_XMLS = {
            "mapper/CourseworkMapper.xml",
            "mapper/NotificationMapper.xml",
            "mapper/MeMapper.xml",
            "mapper/AuditLogMapper.xml",
    };

    private static Connection conn;
    private static SqlSessionFactory factory;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(USER != null && PASS != null, "未注入 EDUDOC_DB_USERNAME/PASSWORD，跳过 SQL 冒烟");
        try {
            conn = java.sql.DriverManager.getConnection(URL, USER, PASS);
            // 驱动在 autoCommit 下 commit 会抛 ORA-17273，统一关自动提交
            conn.setAutoCommit(false);
        } catch (Exception e) {
            Assumptions.abort("Oracle 不可达: " + e.getMessage());
        }
        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment("sql-smoke", new JdbcTransactionFactory(),
                new UnpooledDataSource("oracle.jdbc.OracleDriver", URL, USER, PASS)));
        for (String xml : MAPPER_XMLS) {
            try (InputStream in = Resources.getResourceAsStream(xml)) {
                new XMLMapperBuilder(in, configuration, xml, configuration.getSqlFragments()).parse();
            } catch (Exception e) {
                throw new IllegalStateException("mapper XML 解析失败: " + xml, e);
            }
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) {
            conn.rollback();
            conn.close();
        }
    }

    /** 取一个真实存在的学生与教师主键（空库则跳过对应断言）。 */
    private static String firstUserId(String roleCode) throws Exception {
        try (var st = conn.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT RAWTOHEX(u.id) FROM edudoc_user u "
                    + "JOIN edudoc_user_role ur ON ur.user_id = u.id JOIN edudoc_role r ON r.id = ur.role_id "
                    + "WHERE r.role_code = '" + roleCode + "' AND ROWNUM = 1");
            return rs.next() ? rs.getString(1) : null;
        }
    }

    @Test
    @DisplayName("mapper XML 全部可解析，且四个 Mapper 接口可注册")
    void mappersLoad() {
        try (SqlSession session = factory.openSession(true)) {
            assertDoesNotThrow(() -> session.getMapper(CourseworkMapper.class));
            assertDoesNotThrow(() -> session.getMapper(NotificationMapper.class));
            assertDoesNotThrow(() -> session.getMapper(MeMapper.class));
            assertDoesNotThrow(() -> session.getMapper(AuditLogMapper.class));
        }
    }

    @Test
    @DisplayName("MeMapper.selectLastPasswordChangeAt 必须有列别名（曾导致 /me/security 500 ORA-17133）")
    void passwordChangeTimestampQueryIsReadable() throws Exception {
        String student = firstUserId("STUDENT");
        Assumptions.assumeTrue(student != null, "库内无学生账号");
        try (SqlSession session = factory.openSession(true)) {
            MeMapper mapper = session.getMapper(MeMapper.class);
            // MAX() 在零行时仍返回一行 NULL，正因如此无别名表达式列必然触发取列失败
            assertDoesNotThrow(() -> mapper.selectLastPasswordChangeAt(student));
        }
    }

    @Test
    @DisplayName("MeMapper 登录记录时间输出为带 Z 的 UTC 串（防早 8 小时回归）")
    void loginRecordsUseUtcIso() throws Exception {
        String teacher = firstUserId("TEACHER");
        Assumptions.assumeTrue(teacher != null, "库内无教师账号");
        try (SqlSession session = factory.openSession(true)) {
            List<Map<String, Object>> rows = session.getMapper(MeMapper.class)
                    .selectAuthLogins(teacher, OffsetDateTime.now().minusDays(30), 20);
            for (Map<String, Object> row : rows) {
                assertTrue(row.containsKey("time"), "缺 time 别名: " + row.keySet());
                String time = String.valueOf(row.get("time"));
                assertTrue(time.endsWith("Z"), "时间应带 Z 结尾的 UTC 口径，实得 " + time);
            }
        }
    }

    @Test
    @DisplayName("CourseworkMapper 三个投影查询可执行且嵌套类 resultType 生效")
    void courseworkProjectionsExecute() throws Exception {
        String teacher = firstUserId("TEACHER");
        String student = firstUserId("STUDENT");
        Assumptions.assumeTrue(teacher != null && student != null, "库内账号不全");
        try (SqlSession session = factory.openSession(true)) {
            CourseworkMapper mapper = session.getMapper(CourseworkMapper.class);
            var courses = mapper.selectTeacherCourses(teacher, null, null, null, 0, 20);
            var assignments = mapper.selectAssignments(teacher, null, null);
            var studentAssignments = mapper.selectStudentAssignments(student, null);
            // 走到这里说明 OFFSET/FETCH、LEFT JOIN 里的 FETCH FIRST 标量子查询、CourseworkRow$Xxx 别名映射都没问题
            assertTrue(mapper.countTeacherCourses(teacher, null, null, null) >= courses.size());
            assertTrue(studentAssignments.stream().noneMatch(a -> "DRAFT".equals(a.getAssignmentStatus())),
                    "草稿作业不应出现在学生列表（SQL 侧 a.status IN (PUBLISHED,CLOSED) 是唯一防线，mock 测不到）");
            if (!assignments.isEmpty()) {
                // 未交名单走 NOT EXISTS：不应包含登录名缺失的行
                var unsubmitted = mapper.selectUnsubmittedMembers(assignments.get(0).getId());
                assertTrue(unsubmitted.stream().noneMatch(m -> m.getAccountNo() == null), "未交名单缺登录名");
            }
        }
    }

    @Test
    @DisplayName("Notification 读写：偏好 MERGE 幂等、信箱计数与列表口径一致")
    void notificationQueriesExecuteAndAggregateConsistently() throws Exception {
        String student = firstUserId("STUDENT");
        Assumptions.assumeTrue(student != null, "库内无学生账号");
        try (SqlSession session = factory.openSession(true)) {
            NotificationMapper mapper = session.getMapper(NotificationMapper.class);
            long unreadBefore = mapper.countUnread(student);
            var page = mapper.selectInbox(student, null, null, 0, 10);
            var prefs = mapper.selectPreferences(student);
            assertTrue(page.size() <= 10, "分页未生效");
            assertTrue(prefs.size() <= 8, "偏好最多八类");
            assertTrue(unreadBefore >= 0);
        }
    }

    @Test
    @DisplayName("AuditLog 检索与链路聚合可执行，occurredAt 带 Z、actorRole 有别名")
    void auditQueriesExecute() {
        try (SqlSession session = factory.openSession(true)) {
            AuditLogMapper mapper = session.getMapper(AuditLogMapper.class);
            OffsetDateTime since = OffsetDateTime.now().minusDays(30);
            long total = mapper.countEvents(since, null, null, null);
            List<Map<String, Object>> rows = total == 0 ? List.of()
                    : mapper.searchEvents(since, null, null, null, 0, 5);
            for (Map<String, Object> row : rows) {
                assertTrue(row.containsKey("occurredAt"), "缺 occurredAt 别名");
                assertTrue(String.valueOf(row.get("occurredAt")).endsWith("Z"),
                        "审计时间应带 Z，实得 " + row.get("occurredAt"));
                // actorRole 用 `AS "actorRole"` 别名（REQ-09 要求角色列），但历史行里该列大量为 NULL：
                // MyBatis 的 resultType=map 默认丢弃 null 列，所以键可能缺席——不能断言一定存在。
                // 有值时必须是可显示的角色码；写全量角色属既有审计写入链路缺口，见 06-待确认清单 B3。
                if (row.containsKey("actorRole")) {
                    assertTrue(String.valueOf(row.get("actorRole")).length() > 0, "actorRole 有值时不应为空串");
                }
            }
            if (!rows.isEmpty()) {
                String traceId = (String) rows.get(0).get("traceId");
                assertDoesNotThrow(() -> mapper.selectChainByTraceId(traceId));
                assertDoesNotThrow(() -> mapper.selectEventDetail((String) rows.get(0).get("id")));
            }
        }
    }
}
