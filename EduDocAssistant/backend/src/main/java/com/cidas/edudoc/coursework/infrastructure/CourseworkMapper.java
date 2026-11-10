package com.cidas.edudoc.coursework.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * coursework 域 SQL 入口（全部语句在 {@code resources/mapper/CourseworkMapper.xml}，Java 侧不写 SQL）。
 *
 * <p>本域读写横跨 course / course_member / assignment / submission / document / format_check 六张表，
 * 集中一个 Mapper 便于对照 06 计划的端点清单；写语句按表分组并注明归属。
 * 主键出入参一律 32 位大写十六进制串（{@code RAWTOHEX}/{@code HEXTORAW} 转换在 XML 内完成）。
 *
 * <p>教师侧方法的 {@code userIdHex} 一律指当前登录教师：XML 里的
 * {@code teacherCourseScope} 片段按该名取课程范围，改名会静默改变可见范围，勿动。
 */
@Mapper
public interface CourseworkMapper {

    // ---------- 课程（edudoc_course / edudoc_course_member） ----------

    List<CourseworkRow.TeacherCourse> selectTeacherCourses(@Param("userIdHex") String userIdHex,
                                                           @Param("term") String term,
                                                           @Param("status") String status,
                                                           @Param("keyword") String keyword,
                                                           @Param("offset") int offset,
                                                           @Param("limit") int limit);

    long countTeacherCourses(@Param("userIdHex") String userIdHex, @Param("term") String term,
                             @Param("status") String status, @Param("keyword") String keyword);

    /** 新建课程：course_code + term_code 复合唯一（uk_course_1），重复由上层前置校验。 */
    int insertCourse(@Param("courseCode") String courseCode, @Param("courseName") String courseName,
                     @Param("termCode") String termCode, @Param("teacherIdHex") String teacherIdHex);

    String selectCourseIdHex(@Param("courseCode") String courseCode, @Param("termCode") String termCode);

    /** 按登录名定位用户（成员导入用；学号即 username）。 */
    CourseworkRow.MemberAccount selectUserByAccount(@Param("accountNo") String accountNo);

    long countCourseMember(@Param("courseIdHex") String courseIdHex, @Param("userIdHex") String userIdHex);

    int insertCourseMember(@Param("courseIdHex") String courseIdHex, @Param("userIdHex") String userIdHex,
                           @Param("memberRole") String memberRole, @Param("groupName") String groupName);

    /** 名单里无账号的学生：建未激活账号（account_status='I'），走统一身份认证首登激活。 */
    int insertInactiveUser(@Param("accountNo") String accountNo, @Param("name") String name);

    int updateCourseStatus(@Param("courseIdHex") String courseIdHex, @Param("status") String status);

    // ---------- 作业（edudoc_assignment） ----------

    List<CourseworkRow.AssignmentStats> selectAssignments(@Param("userIdHex") String userIdHex,
                                                          @Param("courseIdHex") String courseIdHex,
                                                          @Param("status") String status);

    CourseworkRow.AssignmentStats selectAssignmentById(@Param("assignmentIdHex") String assignmentIdHex);

    int insertAssignment(@Param("courseIdHex") String courseIdHex, @Param("title") String title,
                         @Param("description") String description, @Param("publishedByHex") String publishedByHex,
                         @Param("dueTime") OffsetDateTime dueTime, @Param("allowLate") String allowLate,
                         @Param("submissionFormat") String submissionFormat,
                         @Param("requireFormatCheck") String requireFormatCheck,
                         @Param("rubricJson") String rubricJson, @Param("status") String status);

    String selectAssignmentId(@Param("courseIdHex") String courseIdHex, @Param("title") String title);

    /** 未提交学生名单（提醒入口，投递随消息切片）。 */
    List<CourseworkRow.MemberAccount> selectUnsubmittedMembers(@Param("assignmentIdHex") String assignmentIdHex);

    /** 教师工作台：本人课程内已发布作业按截止升序 + 提交缺口。 */
    List<CourseworkRow.Priority> selectPriorityAssignments(@Param("userIdHex") String userIdHex,
                                                           @Param("limit") int limit);

    long countDueThisWeek(@Param("userIdHex") String userIdHex, @Param("from") OffsetDateTime from,
                          @Param("to") OffsetDateTime to);

    long countPendingGrade(@Param("userIdHex") String userIdHex);

    long countFormatIssues(@Param("userIdHex") String userIdHex);

    // ---------- 学生作业与提交（edudoc_submission） ----------

    /** 学生可见作业：本人所在课程的 PUBLISHED/CLOSED 作业 + 本人最新一次提交。 */
    List<CourseworkRow.StudentAssignment> selectStudentAssignments(@Param("studentIdHex") String studentIdHex,
                                                                  @Param("courseIdHex") String courseIdHex);

    /** 提交候选文档：本人 owner 且状态 READY（跨读 document 域表，只取提交需要的列）。 */
    CourseworkRow.OwnedDocument selectOwnedReadyDocument(@Param("studentIdHex") String studentIdHex,
                                                         @Param("documentIdHex") String documentIdHex);

    long countSubmissions(@Param("assignmentIdHex") String assignmentIdHex,
                          @Param("studentIdHex") String studentIdHex);

    int insertSubmission(@Param("assignmentIdHex") String assignmentIdHex, @Param("studentIdHex") String studentIdHex,
                         @Param("documentIdHex") String documentIdHex,
                         @Param("documentVersionIdHex") String documentVersionIdHex,
                         @Param("formatCheckIdHex") String formatCheckIdHex, @Param("attemptNo") long attemptNo,
                         @Param("status") String status);

    String selectSubmissionId(@Param("assignmentIdHex") String assignmentIdHex,
                              @Param("studentIdHex") String studentIdHex, @Param("attemptNo") long attemptNo);

    List<CourseworkRow.Submission> selectSubmissions(@Param("userIdHex") String userIdHex,
                                                    @Param("assignmentIdHex") String assignmentIdHex,
                                                    @Param("status") String status);

    int updateSubmissionGrade(@Param("submissionIdHex") String submissionIdHex, @Param("score") BigDecimal score,
                              @Param("feedback") String feedback, @Param("scoreJson") String scoreJson,
                              @Param("status") String status);
}
