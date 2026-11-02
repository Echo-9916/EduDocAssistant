package com.cidas.edudoc.access.infrastructure;

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
import java.util.ArrayList;
import java.util.List;

/** 启动期种子账号：读取 db_seed/seed_accounts.csv，幂等写入 edudoc_user 与角色关联。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeedDataRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String csv = StreamUtils.copyToString(
                new ClassPathResource("db_seed/seed_accounts.csv").getInputStream(),
                StandardCharsets.UTF_8);
        seed(parse(csv));
        seedCourses();
    }

    /** 种子课程：teacher 授两门课，student 仅选 CS101（用于课程范围与列表筛选联调）。 */
    void seedCourses() {
        seedCourse("CS101", "计算机导论", "2026-FALL",
                new String[][]{{"teacher", "TEACHER"}, {"student", "STUDENT"}});
        seedCourse("MATH201", "高等数学", "2026-FALL",
                new String[][]{{"teacher", "TEACHER"}});
    }

    private void seedCourse(String courseCode, String courseName, String termCode, String[][] members) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edudoc_course WHERE course_code = ? AND term_code = ?",
                Integer.class, courseCode, termCode);
        if (exists != null && exists > 0) {
            log.info("种子课程已存在，跳过：{}（{}）", courseCode, termCode);
            return;
        }
        String teacherIdHex = queryUserId("teacher");
        jdbcTemplate.update(
                "INSERT INTO edudoc_course (course_code, course_name, term_code, teacher_id) "
                        + "VALUES (?, ?, ?, HEXTORAW(?))",
                courseCode, courseName, termCode, teacherIdHex);
        String courseIdHex = jdbcTemplate.queryForObject(
                "SELECT RAWTOHEX(id) FROM edudoc_course WHERE course_code = ? AND term_code = ?",
                String.class, courseCode, termCode);
        for (String[] member : members) {
            jdbcTemplate.update(
                    "INSERT INTO edudoc_course_member (course_id, user_id, member_role) "
                            + "SELECT HEXTORAW(?), id, ? FROM edudoc_user WHERE username = ?",
                    courseIdHex, member[1], member[0]);
        }
        log.info("种子课程已创建：{}（{}，成员 {} 人）", courseCode, termCode, members.length);
    }

    private String queryUserId(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT RAWTOHEX(id) FROM edudoc_user WHERE username = ?", String.class, username);
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
