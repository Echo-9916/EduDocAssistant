package com.cidas.edudoc.access.infrastructure;

import com.cidas.edudoc.ai.infrastructure.AiModelProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 启动期种子账号：读取 db_seed/seed_accounts.csv，幂等写入 edudoc_user 与角色关联。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeedDataRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AiModelProperties aiModelProperties;

    /** 演示账号显示名：对齐原型人物（张同学 / 李老师 / 赵工）。 */
    private static final Map<String, String> DEMO_DISPLAY_NAMES =
            Map.of("student", "张同学", "teacher", "李老师", "ops", "赵工");

    /** 演示评分标准：五项各 20 分，与教师端发布作业表单默认口径一致。 */
    private static final String DEMO_RUBRIC_JSON =
            "[{\"name\":\"需求分析\",\"maxScore\":20},{\"name\":\"系统设计\",\"maxScore\":20},"
                    + "{\"name\":\"实现与测试\",\"maxScore\":20},{\"name\":\"格式规范\",\"maxScore\":20},"
                    + "{\"name\":\"结论与思考\",\"maxScore\":20}]";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String csv = StreamUtils.copyToString(
                new ClassPathResource("db_seed/seed_accounts.csv").getInputStream(),
                StandardCharsets.UTF_8);
        seed(parse(csv));
        alignDemoIdentity();
        seedCourses();
        seedCoursework();
        seedAiModelProfiles();
    }

    /** C-02 模型档案：三条 profile 幂等 seed（LLM_MAIN / EMB_BGE_M3 / RERANK_BGE_V2），不存明文密钥。 */
    void seedAiModelProfiles() {
        seedProfile("LLM_MAIN", "LLM", aiModelProperties.llm().model(),
                "edudoc.ai.llm.base-url", null, null,
                "{\"temperature\":" + aiModelProperties.llm().temperature() + ",\"maxTokens\":2000}");
        seedProfile("EMB_BGE_M3", "EMBEDDING", aiModelProperties.embedding().model(),
                "edudoc.ai.embedding.base-url", aiModelProperties.embedding().dimension(), "COSINE",
                "{\"batchMax\":25}");
        seedProfile("RERANK_BGE_V2", "RERANK", aiModelProperties.rerank().model(),
                "edudoc.ai.rerank.base-url", null, null, null);
    }

    private void seedProfile(String code, String providerType, String modelName, String endpointConfigKey,
                             Integer dimension, String metricType, String profileJson) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edudoc_ai_model_profile WHERE profile_code = ?",
                Integer.class, code);
        if (exists != null && exists > 0) {
            log.info("模型档案已存在，跳过：{}", code);
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO edudoc_ai_model_profile (profile_code, provider_type, model_name,"
                        + " endpoint_config_key, embedding_dimension, metric_type, status, profile_json)"
                        + " VALUES (?, ?, ?, ?, ?, ?, 'A', ?)",
                code, providerType, modelName, endpointConfigKey, dimension, metricType, profileJson);
        log.info("模型档案已创建：{}（{}，模型 {}）", code, providerType, modelName);
    }

    /** 演示世界观对齐原型（docs/prototype/screens）：账号显示名统一，旧库改名幂等生效。 */
    void alignDemoIdentity() {
        DEMO_DISPLAY_NAMES.forEach((username, displayName) -> jdbcTemplate.update(
                "UPDATE edudoc_user SET display_name = ?, update_time = SYSTIMESTAMP WHERE username = ?",
                displayName, username));
    }

    /**
     * 种子课程：teacher 授两门课，student 选两门（作业列表与提交统计需要多课程可见）。
     * 课程名对齐原型《Web开发技术》《数据库原理》，旧库遗留名（计算机导论/高等数学）幂等改名。
     */
    void seedCourses() {
        // 第三列是课程组名：edudoc_course_member.group_name，课程列表的「班级/课程组数」据此现算
        seedCourse("CS101", "Web开发技术", "2026-FALL",
                new String[][]{{"teacher", "TEACHER", null}, {"student", "STUDENT", "Web开发-1组"}});
        seedCourse("MATH201", "数据库原理", "2026-FALL",
                new String[][]{{"teacher", "TEACHER", null}, {"student", "STUDENT", "数据库-1组"}});
    }

    private void seedCourse(String courseCode, String courseName, String termCode, String[][] members) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edudoc_course WHERE course_code = ? AND term_code = ?",
                Integer.class, courseCode, termCode);
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                    "UPDATE edudoc_course SET course_name = ?, update_time = SYSTIMESTAMP "
                            + "WHERE course_code = ? AND term_code = ? AND course_name <> ?",
                    courseName, courseCode, termCode, courseName);
            String courseIdHex = queryCourseIdHex(courseCode, termCode);
            for (String[] member : members) {
                seedMember(courseIdHex, member[0], member[1], member[2]);
            }
            log.info("种子课程已存在，补齐名称与成员：{}（{}）", courseCode, termCode);
            return;
        }
        String teacherIdHex = queryUserId("teacher");
        jdbcTemplate.update(
                "INSERT INTO edudoc_course (course_code, course_name, term_code, teacher_id) "
                        + "VALUES (?, ?, ?, HEXTORAW(?))",
                courseCode, courseName, termCode, teacherIdHex);
        String courseIdHex = queryCourseIdHex(courseCode, termCode);
        for (String[] member : members) {
            seedMember(courseIdHex, member[0], member[1], member[2]);
        }
        log.info("种子课程已创建：{}（{}，成员 {} 人）", courseCode, termCode, members.length);
    }

    /** 成员幂等补插：已存在（uk_course_member_1）则跳过；groupName 供「班级/课程组数」现算。 */
    private void seedMember(String courseIdHex, String username, String memberRole, String groupName) {
        jdbcTemplate.update(
                "INSERT INTO edudoc_course_member (course_id, user_id, member_role, group_name) "
                        + "SELECT HEXTORAW(?), u.id, ?, ? FROM edudoc_user u WHERE u.username = ? "
                        + "AND NOT EXISTS (SELECT 1 FROM edudoc_course_member m "
                        + "WHERE m.course_id = HEXTORAW(?) AND m.user_id = u.id)",
                courseIdHex, memberRole, groupName, username, courseIdHex);
        // 早期种子已入课但无组别：补一次组名，否则课程列表的「班级/课程组数」恒为 0
        if (groupName != null) {
            jdbcTemplate.update(
                    "UPDATE edudoc_course_member SET group_name = ?, update_time = SYSTIMESTAMP "
                            + "WHERE course_id = HEXTORAW(?) AND group_name IS NULL AND member_role = 'STUDENT' "
                            + "AND user_id = (SELECT id FROM edudoc_user WHERE username = ?)",
                    groupName, courseIdHex, username);
        }
    }

    /**
     * 种子作业：一门课一份已发布作业（含评分标准，提交不强制格式校验，保证演示不依赖真上传）、
     * 一门课一份草稿作业（验证 DRAFT 不出现在学生列表）。重复执行按标题去重。
     */
    void seedCoursework() {
        seedAssignment("CS101", "Web开发课程设计报告",
                "按课程模板完成设计报告，含需求分析、系统设计、实现与测试、格式规范、结论与思考。",
                "2026-11-15T23:59:59+08:00", "PUBLISHED", "N");
        seedAssignment("MATH201", "索引设计实验",
                "完成 B+ 树索引实验并给出建表与查询计划对比。",
                "2026-11-20T23:59:59+08:00", "DRAFT", "N");
    }

    private void seedAssignment(String courseCode, String title, String description,
                                String dueTime, String status, String requireFormatCheck) {
        String courseIdHex = queryCourseIdHex(courseCode, "2026-FALL");
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edudoc_assignment WHERE course_id = HEXTORAW(?) AND title = ?",
                Integer.class, courseIdHex, title);
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO edudoc_assignment (course_id, title, description, published_by, due_time, "
                        + "allow_late, submission_format, require_format_check, rubric_json, status) "
                        + "VALUES (HEXTORAW(?), ?, ?, HEXTORAW(?), ?, 'Y', 'BOTH', ?, ?, ?)",
                courseIdHex, title, description, queryUserId("teacher"), OffsetDateTime.parse(dueTime),
                requireFormatCheck, DEMO_RUBRIC_JSON, status);
        log.info("种子作业已创建：{}（{}，状态 {}）", title, courseCode, status);
    }

    private String queryUserId(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT RAWTOHEX(id) FROM edudoc_user WHERE username = ?", String.class, username);
    }

    private String queryCourseIdHex(String courseCode, String termCode) {
        return jdbcTemplate.queryForObject(
                "SELECT RAWTOHEX(id) FROM edudoc_course WHERE course_code = ? AND term_code = ?",
                String.class, courseCode, termCode);
    }

    void seed(List<SeedAccount> accounts) {
        for (SeedAccount account : accounts) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM edudoc_user WHERE username = ?", Integer.class, account.username());
            if (exists != null && exists > 0) {
                log.info("种子账号已存在，跳过：{}", account.username());
                continue;
            }
            jdbcTemplate.update(
                    "INSERT INTO edudoc_user (username, display_name, auth_source, password_hash, account_status) "
                            + "VALUES (?, ?, 'LOCAL', ?, ?)",
                    account.username(), account.displayName(), passwordEncoder.encode(account.password()), account.status());
            String userIdHex = jdbcTemplate.queryForObject(
                    "SELECT RAWTOHEX(id) FROM edudoc_user WHERE username = ?", String.class, account.username());
            jdbcTemplate.update(
                    "INSERT INTO edudoc_user_role (user_id, role_id) SELECT HEXTORAW(?), id FROM edudoc_role WHERE role_code = ?",
                    userIdHex, account.roleCode());
            log.info("种子账号已创建：{}（角色 {}，状态 {}）", account.username(), account.roleCode(), account.status());
        }
    }

    static List<SeedAccount> parse(String csv) {
        List<SeedAccount> accounts = new ArrayList<>();
        for (String line : csv.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] f = trimmed.split(",", -1);
            accounts.add(new SeedAccount(f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim(), f[4].trim()));
        }
        return accounts;
    }

    record SeedAccount(String username, String displayName, String roleCode, String status, String password) {
    }
}
