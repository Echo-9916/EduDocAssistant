package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.document.infrastructure.DocumentMapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-15 真库 SQL 冒烟（[IT-SKIP] 先例同 ChunkEmbeddingVectorIntegrationTest）：
 * 覆盖 04 切片四条真实 Oracle 新语句——template_version.parse_error 往返、
 * format_check selectActive 30 分钟窗口（RUNNING 过期不阻塞、QUEUED 不受限）+ markRunning CAS、
 * document selectForUpdate/updateCurrentVersionCas、document_element JSON_VALUE 段落序排序。
 * Oracle 不可达或未显式注入凭据时整体跳过，不阻断离线构建；跳过时显式告警。
 */
class TemplateFormatSqlIntegrationTest {

    private static final String URL = System.getenv()
            .getOrDefault("EDUDOC_DB_URL", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
    private static final String USER = System.getenv("EDUDOC_DB_USERNAME");
    private static final String PASS = System.getenv("EDUDOC_DB_PASSWORD");

    private static final String USER_ID = "BF000000000000000000000000000001";
    private static final String OBJECT_ID = "BF000000000000000000000000000002";
    private static final String DOC_ID = "BF000000000000000000000000000003";
    private static final String DOCVER_V1 = "BF000000000000000000000000000004";
    private static final String TEMPLATE_ID = "BF000000000000000000000000000005";
    private static final String TPLVER_ID = "BF000000000000000000000000000006";
    private static final String DOCVER_V2 = "BF000000000000000000000000000007";
    private static final String TPLVER_PARSE = "BF00000000000000000000000000000A";
    private static final String CHECK_ID = "BF00000000000000000000000000000B";
    private static final String CHECK_ID2 = "BF00000000000000000000000000000C";

    private static Connection setupConn;
    private static SqlSessionFactory factory;

    @BeforeAll
    static void setUp() throws Exception {
        if (USER == null || PASS == null) {
            System.err.println("[IT-SKIP] TemplateFormatSqlIntegrationTest 未执行：需注入 "
                    + "EDUDOC_DB_USERNAME/EDUDOC_DB_PASSWORD，本轮全绿不含 04 新 SQL 真库覆盖");
        }
        Assumptions.assumeTrue(USER != null && PASS != null, "未显式注入数据库凭据，跳过 04 真库 SQL 冒烟");
        try {
            setupConn = java.sql.DriverManager.getConnection(URL, USER, PASS);
        } catch (Exception e) {
            System.err.println("[IT-SKIP] TemplateFormatSqlIntegrationTest Oracle 不可达: " + e.getMessage());
            Assumptions.abort("Oracle 不可达: " + e.getMessage());
        }
        // cleanup/seed 走显式 commit：Oracle 驱动在 autoCommit 下 commit 抛 ORA-17273
        setupConn.setAutoCommit(false);
        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment("fmt-it",
                new JdbcTransactionFactory(),
                new UnpooledDataSource("oracle.jdbc.OracleDriver", URL, USER, PASS)));
        for (String xml : new String[]{"mapper/TemplateVersionMapper.xml", "mapper/FormatCheckMapper.xml",
                "mapper/DocumentElementMapper.xml", "mapper/DocumentMapper.xml"}) {
            try (InputStream in = Resources.getResourceAsStream(xml)) {
                new XMLMapperBuilder(in, configuration, xml, configuration.getSqlFragments()).parse();
            }
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);
        cleanup();
        seedChain();
    }

    /** 固定 ID 链条：user → oss_object → document(v1 为当前版) → doc v1/v2 → template → tplver。 */
    private static void seedChain() throws Exception {
        try (Statement st = setupConn.createStatement()) {
            st.execute("INSERT INTO edudoc_user (id, username, display_name, auth_source, account_status) "
                    + "VALUES (HEXTORAW('" + USER_ID + "'), 'fmt_it_user', '格式冒烟', 'LOCAL', 'A')");
            st.execute("INSERT INTO edudoc_oss_object (id, bucket_name, region, object_key, size_bytes, "
                    + "mime_type, encryption_type, object_role, content_hash) VALUES (HEXTORAW('" + OBJECT_ID + "'), "
                    + "'b', 'r', 'k', 1, 'application/octet-stream', 'SSE-OSS', 'ORIGINAL', 'h')");
            st.execute("INSERT INTO edudoc_document (id, owner_id, title, doc_type, status, current_version_id) "
                    + "VALUES (HEXTORAW('" + DOC_ID + "'), HEXTORAW('" + USER_ID + "'), "
                    + "'it', 'REPORT', 'READY', HEXTORAW('" + DOCVER_V1 + "'))");
            st.execute("INSERT INTO edudoc_document_version (id, document_id, version_no, version_type, "
                    + "source_object_id, editor_id, content_hash, version_status) VALUES (HEXTORAW('" + DOCVER_V1 + "'), "
                    + "HEXTORAW('" + DOC_ID + "'), 1, 'UPLOAD', HEXTORAW('" + OBJECT_ID + "'), HEXTORAW('" + USER_ID + "'), 'h', 'A')");
            st.execute("INSERT INTO edudoc_document_version (id, document_id, version_no, version_type, "
                    + "source_object_id, editor_id, content_hash, version_status) VALUES (HEXTORAW('" + DOCVER_V2 + "'), "
                    + "HEXTORAW('" + DOC_ID + "'), 2, 'EDIT', HEXTORAW('" + OBJECT_ID + "'), HEXTORAW('" + USER_ID + "'), 'h2', 'A')");
            st.execute("INSERT INTO edudoc_template (id, template_name, owner_id, status) "
                    + "VALUES (HEXTORAW('" + TEMPLATE_ID + "'), 'fmt_it_tpl', HEXTORAW('" + USER_ID + "'), 'D')");
            st.execute("INSERT INTO edudoc_template_version (id, template_id, version_no, validation_status) "
                    + "VALUES (HEXTORAW('" + TPLVER_ID + "'), HEXTORAW('" + TEMPLATE_ID + "'), 1, 'VALID')");
        }
        setupConn.commit();
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
            st.execute("DELETE FROM edudoc_document_element WHERE document_version_id IN "
                    + "(HEXTORAW('" + DOCVER_V1 + "'), HEXTORAW('" + DOCVER_V2 + "'))");
            st.execute("DELETE FROM edudoc_format_check WHERE id IN (HEXTORAW('" + CHECK_ID + "'), "
                    + "HEXTORAW('" + CHECK_ID2 + "'))");
            st.execute("DELETE FROM edudoc_template_version WHERE id IN (HEXTORAW('" + TPLVER_ID + "'), "
                    + "HEXTORAW('" + TPLVER_PARSE + "'))");
            st.execute("DELETE FROM edudoc_template WHERE id = HEXTORAW('" + TEMPLATE_ID + "')");
            st.execute("DELETE FROM edudoc_document_version WHERE id IN (HEXTORAW('" + DOCVER_V1 + "'), "
                    + "HEXTORAW('" + DOCVER_V2 + "'))");
            st.execute("DELETE FROM edudoc_document WHERE id = HEXTORAW('" + DOC_ID + "')");
            st.execute("DELETE FROM edudoc_oss_object WHERE id = HEXTORAW('" + OBJECT_ID + "')");
            st.execute("DELETE FROM edudoc_user WHERE id = HEXTORAW('" + USER_ID + "')");
        }
        setupConn.commit();
    }

    /** parse_error（1106 新增列）随 mapper insert/selectById 全链路往返。 */
    @Test
    void parseErrorRoundTrip() {
        try (SqlSession session = factory.openSession(true)) {
            TemplateVersionMapper mapper = session.getMapper(TemplateVersionMapper.class);
            TemplateVersion v = new TemplateVersion();
            v.setId(TPLVER_PARSE);
            v.setTemplateId(TEMPLATE_ID);
            v.setVersionNo(2);
            v.setValidationStatus("INVALID");
            v.setParseError("PARSE_FAILED: styles.xml 缺失");
            assertEquals(1, mapper.insert(v));
            TemplateVersion loaded = mapper.selectById(TPLVER_PARSE);
            assertNotNull(loaded);
            assertEquals("PARSE_FAILED: styles.xml 缺失", loaded.getParseError());
            assertEquals("INVALID", loaded.getValidationStatus());
            // 既有版本行为对照：未写 parse_error 读出 null
            assertNull(mapper.selectById(TPLVER_ID).getParseError());
        }
    }

    /** 1107 P2 口径：QUEUED 不受限、RUNNING 超 30 分钟不再阻塞重发；markRunning 仅 QUEUED→RUNNING。 */
    @Test
    void selectActiveWindowAndMarkRunningCas() throws Exception {
        try (SqlSession session = factory.openSession(true)) {
            FormatCheckMapper mapper = session.getMapper(FormatCheckMapper.class);
            FormatCheck c = new FormatCheck();
            c.setId(CHECK_ID);
            c.setDocumentId(DOC_ID);
            c.setDocumentVersionId(DOCVER_V1);
            c.setTemplateVersionId(TPLVER_ID);
            c.setRequestedBy(USER_ID);
            c.setStatus("QUEUED");
            assertEquals(1, mapper.insert(c));

            assertEquals(CHECK_ID, mapper.selectActive(DOCVER_V1, TPLVER_ID, USER_ID).getId());
            assertEquals(1, mapper.markRunning(CHECK_ID));
            assertEquals(0, mapper.markRunning(CHECK_ID), "RUNNING 上再 CAS 应为 0 行");
            assertEquals(CHECK_ID, mapper.selectActive(DOCVER_V1, TPLVER_ID, USER_ID).getId(), "新鲜 RUNNING 仍在窗口内");

            try (Statement st = setupConn.createStatement()) {
                st.execute("UPDATE edudoc_format_check SET started_at = TIMESTAMP '2020-01-01 00:00:00' "
                        + "WHERE id = HEXTORAW('" + CHECK_ID + "')");
            }
            setupConn.commit(); // session 用独立连接，须提交后对其可见
            session.clearCache(); // 同语句同参数会命中 MyBatis 一级缓存，绕过则看不到外部变更
            assertNull(mapper.selectActive(DOCVER_V1, TPLVER_ID, USER_ID), "超窗 RUNNING 不再阻塞");

            FormatCheck c2 = new FormatCheck();
            c2.setId(CHECK_ID2);
            c2.setDocumentId(DOC_ID);
            c2.setDocumentVersionId(DOCVER_V1);
            c2.setTemplateVersionId(TPLVER_ID);
            c2.setRequestedBy(USER_ID);
            c2.setStatus("QUEUED");
            assertEquals(1, mapper.insert(c2));
            assertEquals(CHECK_ID2, mapper.selectActive(DOCVER_V1, TPLVER_ID, USER_ID).getId(),
                    "重发的 QUEUED 应命中（create_time DESC 取最新）");
        }
    }

    /** selectForUpdate 行锁 + updateCurrentVersionCas 双向（错配 0 行、命中 1 行）。 */
    @Test
    void forUpdateAndCas() {
        try (SqlSession session = factory.openSession(true)) {
            DocumentMapper mapper = session.getMapper(DocumentMapper.class);
            assertEquals(DOC_ID, mapper.selectForUpdate(DOC_ID));
            assertEquals(0, mapper.updateCurrentVersionCas(DOC_ID, DOCVER_V2, DOCVER_V1),
                    "expected 不匹配应 0 行");
            assertEquals(1, mapper.updateCurrentVersionCas(DOC_ID, DOCVER_V1, DOCVER_V2));
            assertEquals(1, mapper.updateCurrentVersionCas(DOC_ID, DOCVER_V2, DOCVER_V1), "还原 v1");
        }
    }

    /** JSON_VALUE 段落序：11 排在 2/7 之后（字符串序会得 11,2,7，数值序才是 2,7,11）。 */
    @Test
    void elementOrderIsNumericByParagraph() {
        try (SqlSession session = factory.openSession(true)) {
            DocumentElementMapper mapper = session.getMapper(DocumentElementMapper.class);
            java.util.List<DocumentElement> els = new java.util.ArrayList<>();
            els.add(element("E-11", "FIGURE", "11", "11"));
            els.add(element("E-2", "TABLE", "2", "02"));
            els.add(element("E-7", "CODE", "7", "07"));
            assertEquals(3, mapper.insertBatch(els));
            var loaded = mapper.selectByVersion(DOCVER_V1);
            assertEquals(3, loaded.size());
            assertEquals("E-2", loaded.get(0).getElementKey());
            assertEquals("E-7", loaded.get(1).getElementKey());
            assertEquals("E-11", loaded.get(2).getElementKey());
            assertTrue(loaded.get(0).getLocationJson().contains("\"paragraphIndex\":2"),
                    "实际: " + loaded.get(0).getLocationJson());
            assertEquals(3, mapper.deleteByVersion(DOCVER_V1));
        }
    }

    private static DocumentElement element(String key, String type, String paragraphIndex, String idSuffix) {
        DocumentElement e = new DocumentElement();
        e.setId("BF" + "0".repeat(28) + idSuffix);
        e.setDocumentVersionId(DOCVER_V1);
        e.setElementKey(key);
        e.setElementType(type);
        e.setLocationJson("{\"paragraphIndex\":" + paragraphIndex + ",\"charStart\":0,\"charEnd\":9}");
        return e;
    }
}
