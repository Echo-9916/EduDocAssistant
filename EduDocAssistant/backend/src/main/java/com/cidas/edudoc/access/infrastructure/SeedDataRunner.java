package com.cidas.edudoc.access.infrastructure;

import com.cidas.edudoc.ai.infrastructure.AiModelProperties;
import com.cidas.edudoc.common.persistence.Ids;
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

    /** 演示同班学生：名单导入生成的未激活账号（auth_source=ACADEMIC，不可本地登录），只用于作业统计层次。 */
    private static final List<DemoStudent> DEMO_STUDENTS = List.of(
            new DemoStudent("2023001", "陈同学"), new DemoStudent("2023002", "刘同学"),
            new DemoStudent("2023003", "王同学"), new DemoStudent("2023004", "李同学"),
            new DemoStudent("2023005", "赵同学"), new DemoStudent("2023006", "周同学"),
            new DemoStudent("2023007", "吴同学"), new DemoStudent("2023008", "郑同学"),
            new DemoStudent("2023009", "孙同学"), new DemoStudent("2023010", "黄同学"));

    /** 已批改学生的分项得分（与 DEMO_RUBRIC_JSON 同序，五项合计即总分）。 */
    private static final Map<String, String> DEMO_SCORE_JSON = Map.of(
            "2023003", "[{\"name\":\"需求分析\",\"maxScore\":20,\"score\":18},"
                    + "{\"name\":\"系统设计\",\"maxScore\":20,\"score\":18},"
                    + "{\"name\":\"实现与测试\",\"maxScore\":20,\"score\":18},"
                    + "{\"name\":\"格式规范\",\"maxScore\":20,\"score\":17},"
                    + "{\"name\":\"结论与思考\",\"maxScore\":20,\"score\":17}]",
            "2023004", "[{\"name\":\"需求分析\",\"maxScore\":20,\"score\":16},"
                    + "{\"name\":\"系统设计\",\"maxScore\":20,\"score\":15},"
                    + "{\"name\":\"实现与测试\",\"maxScore\":20,\"score\":16},"
                    + "{\"name\":\"格式规范\",\"maxScore\":20,\"score\":15},"
                    + "{\"name\":\"结论与思考\",\"maxScore\":20,\"score\":14}]");

    private static final String DEMO_DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String csv = StreamUtils.copyToString(
                new ClassPathResource("db_seed/seed_accounts.csv").getInputStream(),
                StandardCharsets.UTF_8);
        seed(parse(csv));
        alignDemoIdentity();
        seedDemoStudents();
        seedCourses();
        seedCoursework();
        seedDemoSubmissions();
        seedVersionCompareDemo();
        seedAiModelProfiles();
    }

    /** 演示学生账号幂等补插：未激活、无本地密码，显示名沿用 05 世界观（张/陈/刘同学…）。 */
    void seedDemoStudents() {
        for (DemoStudent student : DEMO_STUDENTS) {
            int inserted = jdbcTemplate.update(
                    "INSERT INTO edudoc_user (username, display_name, auth_source, account_status) "
                            + "SELECT ?, ?, 'ACADEMIC', 'I' FROM dual WHERE NOT EXISTS "
                            + "(SELECT 1 FROM edudoc_user WHERE username = ?)",
                    student.username(), student.displayName(), student.username());
            jdbcTemplate.update(
                    "INSERT INTO edudoc_user_role (user_id, role_id) "
                            + "SELECT u.id, r.id FROM edudoc_user u, edudoc_role r "
                            + "WHERE u.username = ? AND r.role_code = 'STUDENT' AND NOT EXISTS "
                            + "(SELECT 1 FROM edudoc_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id)",
                    student.username());
            if (inserted > 0) {
                log.info("演示学生已创建：{}（{}，未激活）", student.displayName(), student.username());
            }
        }
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
        seedCourse("CS101", "Web开发技术", "2026-FALL", demoMembers("Web开发", 3));
        seedCourse("MATH201", "数据库原理", "2026-FALL", demoMembers("数据库", 2));
    }

    /** 成员表：教师 + 本地演示学生 + 10 名演示学生，按组数轮转落 group_name。 */
    private static String[][] demoMembers(String groupPrefix, int groupCount) {
        List<String[]> members = new ArrayList<>();
        members.add(new String[]{"teacher", "TEACHER", null});
        members.add(new String[]{"student", "STUDENT", groupPrefix + "-1组"});
        for (int i = 0; i < DEMO_STUDENTS.size(); i++) {
            members.add(new String[]{DEMO_STUDENTS.get(i).username(), "STUDENT",
                    groupPrefix + "-" + (i % groupCount + 1) + "组"});
        }
        return members.toArray(String[][]::new);
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

    /**
     * 演示提交：前 4 名学生给已发布作业交作业（2 份已批改、2 份待批改），让教师端统计出现层次。
     * 文档/版本/OSS 对象按学生幂等补插；提交按 (assignment, student, attempt_no=1) 去重。
     */
    void seedDemoSubmissions() {
        String assignmentIdHex = findAssignmentIdHex("CS101", "Web开发课程设计报告");
        if (assignmentIdHex == null) {
            return;
        }
        String[] statuses = {"SUBMITTED", "SUBMITTED", "GRADED", "GRADED"};
        String[] feedbacks = {null, null, "结构完整，索引设计部分需补充执行计划对比。",
                "完成度尚可，格式与结论部分需补充。"};
        double[] scores = {0, 0, 88, 76};
        for (int i = 0; i < statuses.length; i++) {
            DemoStudent student = DEMO_STUDENTS.get(i);
            String documentIdHex = ensureSubmissionDocument(student);
            String versionIdHex = jdbcTemplate.queryForObject(
                    "SELECT RAWTOHEX(current_version_id) FROM edudoc_document WHERE id = HEXTORAW(?)",
                    String.class, documentIdHex);
            boolean graded = "GRADED".equals(statuses[i]);
            jdbcTemplate.update(
                    "INSERT INTO edudoc_submission (assignment_id, student_id, document_id, document_version_id, "
                            + "attempt_no, submitted_at, status, score, feedback, score_json) "
                            + "SELECT HEXTORAW(?), u.id, HEXTORAW(?), HEXTORAW(?), 1, SYSTIMESTAMP, ?, ?, ?, ? "
                            + "FROM edudoc_user u WHERE u.username = ? AND NOT EXISTS "
                            + "(SELECT 1 FROM edudoc_submission s WHERE s.assignment_id = HEXTORAW(?) "
                            + "AND s.student_id = u.id AND s.attempt_no = 1)",
                    assignmentIdHex, documentIdHex, versionIdHex, statuses[i],
                    graded ? scores[i] : null, feedbacks[i],
                    graded ? DEMO_SCORE_JSON.get(student.username()) : null,
                    student.username(), assignmentIdHex);
        }
        log.info("演示提交已就绪：{} 份（已批改 {} 份）", statuses.length,
                (int) java.util.Arrays.stream(statuses).filter("GRADED"::equals).count());
    }

    /** 学生提交用的文档：READY + 版本 1 + 一个演示 OSS 对象，重复启动复用同一行。 */
    private String ensureSubmissionDocument(DemoStudent student) {
        String title = "Web开发课程设计报告_" + student.displayName();
        List<String> existing = jdbcTemplate.queryForList(
                "SELECT RAWTOHEX(d.id) FROM edudoc_document d JOIN edudoc_user u ON u.id = d.owner_id "
                        + "WHERE u.username = ? AND d.title = ?", String.class, student.username(), title);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        String objectKey = "seed/coursework/" + student.username() + ".docx";
        jdbcTemplate.update(
                "INSERT INTO edudoc_oss_object (id, bucket_name, region, object_key, content_hash, size_bytes, "
                        + "mime_type, encryption_type, object_role) "
                        + "SELECT HEXTORAW(?), 'cidas-demo', 'local', ?, ?, 1024, ?, 'SSE-S3', 'ORIGINAL' "
                        + "FROM dual WHERE NOT EXISTS "
                        + "(SELECT 1 FROM edudoc_oss_object WHERE bucket_name = 'cidas-demo' AND object_key = ?)",
                Ids.hex16(), objectKey, "demo-" + student.username(), DEMO_DOCX_MIME, objectKey);
        String objectIdHex = jdbcTemplate.queryForObject(
                "SELECT RAWTOHEX(id) FROM edudoc_oss_object WHERE bucket_name = 'cidas-demo' AND object_key = ?",
                String.class, objectKey);
        String documentIdHex = Ids.hex16();
        String versionIdHex = Ids.hex16();
        jdbcTemplate.update(
                "INSERT INTO edudoc_document (id, course_id, owner_id, title, doc_type, status) "
                        + "SELECT HEXTORAW(?), c.id, u.id, ?, 'HOMEWORK', 'READY' "
                        + "FROM edudoc_course c, edudoc_user u "
                        + "WHERE c.course_code = 'CS101' AND c.term_code = '2026-FALL' AND u.username = ?",
                documentIdHex, title, student.username());
        jdbcTemplate.update(
                "INSERT INTO edudoc_document_version (id, document_id, version_no, version_type, source_object_id, "
                        + "editor_id, change_summary, content_hash) "
                        + "SELECT HEXTORAW(?), HEXTORAW(?), 1, 'UPLOAD', HEXTORAW(?), u.id, '课程作业提交', ? "
                        + "FROM edudoc_user u WHERE u.username = ?",
                versionIdHex, documentIdHex, objectIdHex, "demo-" + student.username(), student.username());
        jdbcTemplate.update("UPDATE edudoc_document SET current_version_id = HEXTORAW(?) WHERE id = HEXTORAW(?)",
                versionIdHex, documentIdHex);
        return documentIdHex;
    }

    /**
     * 版本对比演示文档：v1 原文、v2 修订稿（改标题、改内容、删一段、加一段），
     * 两个版本的分块直接写库并标 READY，不依赖解析/向量化 Worker。按 owner+title 幂等。
     */
    void seedVersionCompareDemo() {
        String title = "课程设计报告（演示：版本对比）";
        List<String> existing = jdbcTemplate.queryForList(
                "SELECT RAWTOHEX(d.id) FROM edudoc_document d JOIN edudoc_user u ON u.id = d.owner_id "
                        + "WHERE u.username = 'student' AND d.title = ?", String.class, title);
        if (!existing.isEmpty()) {
            log.info("版本对比演示文档已存在，跳过：{}", existing.get(0));
            return;
        }
        String ownerIdHex = queryUserId("student");
        String courseIdHex = queryCourseIdHex("CS101", "2026-FALL");
        String v1Object = ensureDemoOssObject("seed/version-compare/v1.txt");
        String v2Object = ensureDemoOssObject("seed/version-compare/v2.txt");
        String documentId = Ids.hex16();
        String v1 = Ids.hex16();
        String v2 = Ids.hex16();
        jdbcTemplate.update(
                "INSERT INTO edudoc_document (id, course_id, owner_id, title, doc_type, status, current_version_id) "
                        + "VALUES (HEXTORAW(?), HEXTORAW(?), HEXTORAW(?), ?, 'REPORT', 'READY', HEXTORAW(?))",
                documentId, courseIdHex, ownerIdHex, title, v2);
        insertDemoVersion(v1, documentId, 1, "UPLOAD", v1Object, ownerIdHex, "初稿", "demo-version-v1");
        insertDemoVersion(v2, documentId, 2, "EDIT", v2Object, ownerIdHex,
                "修订稿：改标题、删减后续计划、新增版本恢复说明", "demo-version-v2");
        insertDemoChunks(v1, List.of(
                "一、项目概述",
                "本项目基于 React 与 Spring Boot 实现校园文档辅助系统，面向学生与教师提供文档上传、智能摘要与作业提交能力。",
                "后续计划：补充协作编辑与导出能力，并在下一阶段接入消息通知。",
                "二、总结\n文档接入链路已经稳定，作业提交与批改可以完整演示。"));
        insertDemoChunks(v2, List.of(
                "一、项目背景",
                "本项目基于 React 18 与 Spring Boot 3 实现校园文档辅助系统，面向学生与教师提供文档上传、智能摘要、作业提交与批改统计能力。",
                "新增：版本历史支持恢复与文本对比，恢复不会覆盖历史版本。",
                "二、总结\n文档接入、作业链路与版本恢复均已稳定，可以完整演示。"));
        log.info("版本对比演示文档已创建：{}（文档 {}，v1 {}，v2 {}）", title, documentId, v1, v2);
    }

    private String ensureDemoOssObject(String objectKey) {
        jdbcTemplate.update(
                "INSERT INTO edudoc_oss_object (id, bucket_name, region, object_key, content_hash, size_bytes, "
                        + "mime_type, encryption_type, object_role) "
                        + "SELECT HEXTORAW(?), 'cidas-demo', 'local', ?, ?, 1024, 'text/plain', 'SSE-S3', 'ORIGINAL' "
                        + "FROM dual WHERE NOT EXISTS "
                        + "(SELECT 1 FROM edudoc_oss_object WHERE bucket_name = 'cidas-demo' AND object_key = ?)",
                Ids.hex16(), objectKey, "demo-" + objectKey, objectKey);
        return jdbcTemplate.queryForObject(
                "SELECT RAWTOHEX(id) FROM edudoc_oss_object WHERE bucket_name = 'cidas-demo' AND object_key = ?",
                String.class, objectKey);
    }

    private void insertDemoVersion(String versionIdHex, String documentIdHex, int versionNo, String versionType,
                                   String objectIdHex, String editorIdHex, String changeSummary, String contentHash) {
        jdbcTemplate.update(
                "INSERT INTO edudoc_document_version (id, document_id, version_no, version_type, source_object_id, "
                        + "editor_id, change_summary, content_hash, version_status) "
                        + "VALUES (HEXTORAW(?), HEXTORAW(?), ?, ?, HEXTORAW(?), HEXTORAW(?), ?, ?, 'A')",
                versionIdHex, documentIdHex, versionNo, versionType, objectIdHex, editorIdHex,
                changeSummary, contentHash);
    }

    /** 分块按闭区间密铺：块文本含分隔换行，相邻 char_end + 1 = 后块 char_start。 */
    private void insertDemoChunks(String versionIdHex, List<String> paragraphs) {
        int start = 0;
        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i) + (i < paragraphs.size() - 1 ? "\n" : "");
            int end = start + text.length() - 1;
            jdbcTemplate.update(
                    "INSERT INTO edudoc_document_chunk (id, document_version_id, chunk_no, heading_path, page_no, "
                            + "char_start, char_end, content_text, token_count, content_hash, index_status) "
                            + "VALUES (HEXTORAW(?), HEXTORAW(?), ?, NULL, NULL, ?, ?, ?, ?, ?, 'READY')",
                    Ids.hex16(), versionIdHex, i + 1, start, end, text,
                    Math.max(1, text.length() / 2), "demo-" + versionIdHex + "-" + (i + 1));
            start = end + 1;
        }
    }

    private String findAssignmentIdHex(String courseCode, String title) {
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT RAWTOHEX(a.id) FROM edudoc_assignment a JOIN edudoc_course c ON c.id = a.course_id "
                        + "WHERE c.course_code = ? AND c.term_code = '2026-FALL' AND a.title = ?",
                String.class, courseCode, title);
        return ids.isEmpty() ? null : ids.get(0);
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

    /** 演示同班学生（学号即 username）。 */
    record DemoStudent(String username, String displayName) {
    }
}
