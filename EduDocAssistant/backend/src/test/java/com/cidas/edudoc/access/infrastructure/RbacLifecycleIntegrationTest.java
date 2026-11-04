package com.cidas.edudoc.access.infrastructure;

import com.cidas.edudoc.common.security.UserPrincipal;
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
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RBAC 生命周期集成测试（P1-2 / P3-1）：直接加载 mapper XML 对真实 Oracle 验证
 * 角色/权限查询的 effective_from / effective_to / 角色停用过滤，以及 displayName 契约。
 * Oracle 不可达时整体跳过（assumption），不阻断离线构建。
 */
class RbacLifecycleIntegrationTest {

    private static final String URL = System.getenv()
            .getOrDefault("EDUDOC_DB_URL", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
    // P3-S2：凭据必须显式注入（EDUDOC_DB_USERNAME/EDUDOC_DB_PASSWORD），缺失时跳过，不内置 fallback
    private static final String USER = System.getenv("EDUDOC_DB_USERNAME");
    private static final String PASS = System.getenv("EDUDOC_DB_PASSWORD");

    private static final String USER_ID = "AA0000000000000000000000000000F1";
    private static final String ROLE_ID = "AA0000000000000000000000000000F2";
    private static final String PERM_ID = "AA0000000000000000000000000000F3";
    private static final String USERNAME = "rbac_it_user";

    private static SqlSessionFactory factory;
    private static Connection setupConn;

    @BeforeAll
    static void setUp() throws Exception {
        // 1103 复审 N6：跳过不再是无声 Tests run: 0，显式告警防止"全绿"被误读为真库路径已覆盖
        if (USER == null || PASS == null) {
            System.err.println("[IT-SKIP] RbacLifecycleIntegrationTest 未执行：需注入 "
                    + "EDUDOC_DB_USERNAME/EDUDOC_DB_PASSWORD，本轮全绿不含真库 RBAC 路径覆盖");
        }
        Assumptions.assumeTrue(USER != null && PASS != null,
                "未显式注入 EDUDOC_DB_USERNAME/EDUDOC_DB_PASSWORD，跳过 RBAC 生命周期集成测试");
        try {
            setupConn = java.sql.DriverManager.getConnection(URL, USER, PASS);
        } catch (Exception e) {
            System.err.println("[IT-SKIP] RbacLifecycleIntegrationTest Oracle 不可达，真库路径未覆盖: " + e.getMessage());
            Assumptions.abort("Oracle 不可达，跳过 RBAC 生命周期集成测试: " + e.getMessage());
        }
        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment("rbac-it",
                new JdbcTransactionFactory(), new UnpooledDataSource("oracle.jdbc.OracleDriver", URL, USER, PASS)));
        try (InputStream in = Resources.getResourceAsStream("mapper/UserAccountMapper.xml")) {
            new XMLMapperBuilder(in, configuration, "mapper/UserAccountMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);
        seedFixtures();
    }

    /** 四类关联：有效（STUDENT）/ 未来生效（TEACHER）/ 已过期（OPS）/ 停用角色（临时角色）。 */
    private static void seedFixtures() throws Exception {
        cleanup();
        try (Statement st = setupConn.createStatement()) {
            st.execute("INSERT INTO edudoc_user (id, username, display_name, auth_source, account_status) "
                    + "VALUES (HEXTORAW('" + USER_ID + "'), '" + USERNAME + "', 'RBAC 集成', 'LOCAL', 'A')");
            st.execute("INSERT INTO edudoc_role (id, role_code, role_name, is_builtin, status) "
                    + "VALUES (HEXTORAW('" + ROLE_ID + "'), 'RBAC_IT_DISABLED', '临时停用角色', 'N', 'D')");
            st.execute("INSERT INTO edudoc_permission (id, permission_code, permission_name, resource_type, action_code) "
                    + "VALUES (HEXTORAW('" + PERM_ID + "'), 'RBAC_IT_PERM', '临时权限', 'DOCUMENT', 'READ')");
            st.execute("INSERT INTO edudoc_role_permission (role_id, permission_id) "
                    + "VALUES (HEXTORAW('" + ROLE_ID + "'), HEXTORAW('" + PERM_ID + "'))");
            // 有效：STUDENT，昨天生效、长期有效
            link(st, "STUDENT", "SYSTIMESTAMP - 1", null);
            // 未来生效：TEACHER，明天生效
            link(st, "TEACHER", "SYSTIMESTAMP + 1", null);
            // 已过期：OPS，前天生效、昨天失效
            link(st, "OPS", "SYSTIMESTAMP - 2", "SYSTIMESTAMP - 1");
            // 停用角色：长期有效关联，但角色 status='D'
            st.execute("INSERT INTO edudoc_user_role (user_id, role_id, effective_from) "
                    + "VALUES (HEXTORAW('" + USER_ID + "'), HEXTORAW('" + ROLE_ID + "'), SYSTIMESTAMP - 1)");
        }
    }

    private static void link(Statement st, String roleCode, String from, String to) throws Exception {
        st.execute("INSERT INTO edudoc_user_role (user_id, role_id, effective_from" + (to == null ? "" : ", effective_to") + ") "
                + "SELECT HEXTORAW('" + USER_ID + "'), id, " + from + (to == null ? "" : ", " + to)
                + " FROM edudoc_role WHERE role_code = '" + roleCode + "'");
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (setupConn != null) {
            cleanup();
            setupConn.close();
        }
    }

    private static void cleanup() throws Exception {
        try (Statement st = setupConn.createStatement()) {
            st.execute("DELETE FROM edudoc_user_role WHERE user_id = HEXTORAW('" + USER_ID + "')");
            st.execute("DELETE FROM edudoc_role_permission WHERE role_id = HEXTORAW('" + ROLE_ID + "')");
            st.execute("DELETE FROM edudoc_role WHERE id = HEXTORAW('" + ROLE_ID + "')");
            st.execute("DELETE FROM edudoc_permission WHERE id = HEXTORAW('" + PERM_ID + "')");
            st.execute("DELETE FROM edudoc_user WHERE id = HEXTORAW('" + USER_ID + "')");
        }
    }

    @Test
    void filtersFutureExpiredAndDisabledRolesAndCarriesDisplayName() {
        try (SqlSession session = factory.openSession(true)) {
            // courseScope 非本测试关注点，mock 返回空集合
            UserAccountRepository repo = new UserAccountRepository(session.getMapper(UserAccountMapper.class),
                    org.mockito.Mockito.mock(CourseRepository.class));

            UserPrincipal principal = repo.loadPrincipal(USER_ID, USERNAME);

            assertNotNull(principal);
            // displayName 契约：DB → UserPrincipal（P3-1）
            assertEquals("RBAC 集成", principal.displayName());
            // 仅有效关联的 STUDENT 进入角色集合；未来生效 / 已过期 / 停用角色均被过滤
            assertEquals(java.util.Set.of("STUDENT"), principal.roles());
            // 有效角色贡献权限；停用角色的权限点被过滤
            assertTrue(principal.permissions().contains("DOCUMENT_READ"));
            assertFalse(principal.permissions().contains("RBAC_IT_PERM"));
        }
    }
}
