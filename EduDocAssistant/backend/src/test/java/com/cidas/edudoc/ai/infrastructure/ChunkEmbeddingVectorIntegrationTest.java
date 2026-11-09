package com.cidas.edudoc.ai.infrastructure;

import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.ChunkVector;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-03 真库向量往返（[IT-SKIP] 先例同 RbacLifecycleIntegrationTest）：
 * mapper MERGE upsert（TO_VECTOR 字符串绑定）→ VECTOR_DISTANCE 自检 → 重投覆盖写 → 块 READY。
 * Oracle 不可达或未显式注入凭据时整体跳过，不阻断离线构建；跳过时显式告警。
 */
class ChunkEmbeddingVectorIntegrationTest {

    private static final String URL = System.getenv()
            .getOrDefault("EDUDOC_DB_URL", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
    private static final String USER = System.getenv("EDUDOC_DB_USERNAME");
    private static final String PASS = System.getenv("EDUDOC_DB_PASSWORD");

    private static final String USER_ID = "BB0000000000000000000000000000E1";
    private static final String OBJECT_ID = "BB0000000000000000000000000000E2";
    private static final String DOC_ID = "BB0000000000000000000000000000E3";
    private static final String VERSION_ID = "BB0000000000000000000000000000E4";
    private static final String CHUNK_ID = "BB0000000000000000000000000000E5";
    private static final String PROFILE_ID = "BB0000000000000000000000000000E6";
    private static final String PROFILE_CODE = "EMB_IT_TEST";

    private static Connection setupConn;
    private static SqlSessionFactory factory;

    /** R-A：列固定 VECTOR(1024,FLOAT32)，必须全维往返；1024 元素字符串 ~12KB，正压 VARCHAR2 绑定上限。 */
    private static final int DIM = 1024;
    private static final String VECTOR_TEXT = buildVectorText();

    private static String buildVectorText() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < DIM; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(i == 0 ? "1" : "0");
        }
        return sb.append(']').toString();
    }

    @BeforeAll
    static void setUp() throws Exception {
        if (USER == null || PASS == null) {
            System.err.println("[IT-SKIP] ChunkEmbeddingVectorIntegrationTest 未执行：需注入 "
                    + "EDUDOC_DB_USERNAME/EDUDOC_DB_PASSWORD，本轮全绿不含真库向量往返覆盖");
        }
        Assumptions.assumeTrue(USER != null && PASS != null, "未显式注入数据库凭据，跳过向量往返集成测试");
        try {
            setupConn = java.sql.DriverManager.getConnection(URL, USER, PASS);
        } catch (Exception e) {
            System.err.println("[IT-SKIP] ChunkEmbeddingVectorIntegrationTest Oracle 不可达: " + e.getMessage());
            Assumptions.abort("Oracle 不可达: " + e.getMessage());
        }
        // seedChain/cleanup 走显式 commit：Oracle 驱动在 autoCommit 下 commit 抛 ORA-17273，须先关闭自动提交
        setupConn.setAutoCommit(false);
        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment("embed-it",
                new JdbcTransactionFactory(),
                new UnpooledDataSource("oracle.jdbc.OracleDriver", URL, USER, PASS)));
        try (InputStream in = Resources.getResourceAsStream("mapper/ChunkEmbeddingMapper.xml")) {
            new XMLMapperBuilder(in, configuration, "mapper/ChunkEmbeddingMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);
        seedChain();
    }

    /** 固定 ID 链条：user → oss_object → document → version → chunk(PENDING) → profile。 */
    private static void seedChain() throws Exception {
        cleanup();
        try (Statement st = setupConn.createStatement()) {
            st.execute("INSERT INTO edudoc_user (id, username, display_name, auth_source, account_status) "
                    + "VALUES (HEXTORAW('" + USER_ID + "'), 'embed_it_user', '向量集成', 'LOCAL', 'A')");
            st.execute("INSERT INTO edudoc_oss_object (id, bucket_name, region, object_key, size_bytes, "
                    + "mime_type, encryption_type, object_role, content_hash) VALUES (HEXTORAW('" + OBJECT_ID + "'), "
                    + "'b', 'r', 'k', 1, 'application/pdf', 'SSE-OSS', 'ORIGINAL', 'h')");
            st.execute("INSERT INTO edudoc_document (id, owner_id, title, doc_type, status) "
                    + "VALUES (HEXTORAW('" + DOC_ID + "'), HEXTORAW('" + USER_ID + "'), "
                    + "'it', 'REPORT', 'READY')");
            st.execute("INSERT INTO edudoc_document_version (id, document_id, version_no, version_type, "
                    + "source_object_id, editor_id, content_hash, version_status) VALUES (HEXTORAW('" + VERSION_ID + "'), "
                    + "HEXTORAW('" + DOC_ID + "'), 1, 'UPLOAD', HEXTORAW('" + OBJECT_ID + "'), HEXTORAW('" + USER_ID + "'), 'h', 'A')");
            st.execute("INSERT INTO edudoc_document_chunk (id, document_version_id, chunk_no, char_start, "
                    + "char_end, content_text, token_count, content_hash, index_status) VALUES (HEXTORAW('"
                    + CHUNK_ID + "'), HEXTORAW('" + VERSION_ID + "'), 1, 0, 4, '文本', 2, 'h1', 'PENDING')");
            st.execute("INSERT INTO edudoc_ai_model_profile (id, profile_code, provider_type, model_name, "
                    + "endpoint_config_key, embedding_dimension, metric_type, status) VALUES (HEXTORAW('"
                    + PROFILE_ID + "'), '" + PROFILE_CODE + "', 'EMBEDDING', 'tiny', 'k', 1024, 'COSINE', 'A')");
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
            st.execute("DELETE FROM edudoc_chunk_embedding WHERE chunk_id = HEXTORAW('" + CHUNK_ID + "')");
            st.execute("DELETE FROM edudoc_document_chunk WHERE id = HEXTORAW('" + CHUNK_ID + "')");
            st.execute("DELETE FROM edudoc_document_version WHERE id = HEXTORAW('" + VERSION_ID + "')");
            st.execute("DELETE FROM edudoc_document WHERE id = HEXTORAW('" + DOC_ID + "')");
            st.execute("DELETE FROM edudoc_oss_object WHERE id = HEXTORAW('" + OBJECT_ID + "')");
            st.execute("DELETE FROM edudoc_ai_model_profile WHERE id = HEXTORAW('" + PROFILE_ID + "')");
            st.execute("DELETE FROM edudoc_user WHERE id = HEXTORAW('" + USER_ID + "')");
        }
        setupConn.commit();
    }

    @Test
    void upsertDistanceRoundTripAndRedeliveryOverwrite() throws Exception {
        try (SqlSession session = factory.openSession(true)) {
            ChunkEmbeddingMapper mapper = session.getMapper(ChunkEmbeddingMapper.class);
            List<ChunkEmbeddingRepository.PendingChunk> pending =
                    mapper.selectPendingChunks(VERSION_ID);
            assertEquals(1, pending.size());

            mapper.upsertBatch(PROFILE_ID, DIM, List.of(new ChunkVector(CHUNK_ID, VECTOR_TEXT)));
            mapper.markChunksReady(List.of(CHUNK_ID));

            // 自距离用绑定变量验证（12KB 字符串字面量本身会撞 ORA-01704，绑定才是生产路径）
            try (java.sql.PreparedStatement ps = setupConn.prepareStatement(
                    "SELECT VECTOR_DISTANCE(e.embedding, TO_VECTOR(?, " + DIM + ", FLOAT32), COSINE) "
                            + "FROM edudoc_chunk_embedding e WHERE e.chunk_id = HEXTORAW('" + CHUNK_ID + "')")) {
                ps.setString(1, VECTOR_TEXT);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "向量行应已写入");
                    assertTrue(rs.getDouble(1) < 1e-6, "同向量自距离应≈0，实际 " + rs.getDouble(1));
                }
            }

            // R-A 检索侧：searchByVersion 同样绑 12KB 字符串（生产问答路径）
            List<ChunkEmbeddingRepository.SearchHit> found =
                    mapper.searchByVersion(VERSION_ID, PROFILE_ID, VECTOR_TEXT, DIM, 3);
            assertEquals(1, found.size(), "topK 检索应命中已就绪块");
            assertEquals(CHUNK_ID, found.get(0).chunkId());

            // 重投覆盖写：同 (chunk, profile) uk 不产生第二行
            mapper.upsertBatch(PROFILE_ID, DIM, List.of(new ChunkVector(CHUNK_ID, VECTOR_TEXT)));
            try (Statement st = setupConn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM edudoc_chunk_embedding "
                         + "WHERE chunk_id = HEXTORAW('" + CHUNK_ID + "')")) {
                rs.next();
                assertEquals(1, rs.getInt(1), "重投应覆盖写而非追加");
            }

            // 块状态 READY 且清理后向量随版本删除（FK 无级联路径验证）
            try (Statement st = setupConn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT index_status FROM edudoc_document_chunk "
                         + "WHERE id = HEXTORAW('" + CHUNK_ID + "')")) {
                rs.next();
                assertEquals("READY", rs.getString(1));
            }
            assertEquals(1, mapper.deleteByVersion(VERSION_ID), "按版本清理应删 1 行向量");
        }
    }
}
