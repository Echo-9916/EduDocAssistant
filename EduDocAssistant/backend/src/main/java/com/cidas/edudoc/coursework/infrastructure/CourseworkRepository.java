package com.cidas.edudoc.coursework.infrastructure;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.AssignmentStats;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.MemberAccount;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.OwnedDocument;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.Priority;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.StudentAssignment;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.Submission;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.TeacherCourse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * coursework 域仓储：SQL 全在 {@link CourseworkMapper} 与 XML，本类只做分页装配与唯一约束翻译。
 *
 * <p>主键格式校验（{@code Ids.isHex16}）按既有惯例留在应用服务层，仓储不重复校验：
 * 校验前移到这里的唯一入口是提交写入，撞 {@code uk_submission_1} 时翻译成 40902，
 * 让并发重复提交冒 500 是 06 计划 Review Focus 第 2 条明确要避免的事。
 */
@Repository
@RequiredArgsConstructor
public class CourseworkRepository {

    private final CourseworkMapper mapper;

    // ---------- 课程 ----------

    public PageResult<TeacherCourse> pageTeacherCourses(String userIdHex, String term, String status,
                                                        String keyword, int page, int pageSize) {
        long total = mapper.countTeacherCourses(userIdHex, term, status, keyword);
        List<TeacherCourse> items = mapper.selectTeacherCourses(userIdHex, term, status, keyword,
                (page - 1) * pageSize, pageSize);
        return new PageResult<>(items, total, page, pageSize);
    }

    public String createCourse(String courseCode, String courseName, String termCode, String teacherIdHex) {
        try {
            mapper.insertCourse(courseCode, courseName, termCode, teacherIdHex);
        } catch (DuplicateKeyException e) {
            throw new CidasBusinessException(ErrorCode.DUPLICATE, "课程代码与学期组合已存在");
        }
        return mapper.selectCourseIdHex(courseCode, termCode);
    }

    public MemberAccount findUserByAccount(String accountNo) {
        return mapper.selectUserByAccount(accountNo);
    }

    public boolean isCourseMember(String courseIdHex, String userIdHex) {
        return mapper.countCourseMember(courseIdHex, userIdHex) > 0;
    }

    public void addMember(String courseIdHex, String userIdHex, String memberRole, String groupName) {
        try {
            mapper.insertCourseMember(courseIdHex, userIdHex, memberRole, groupName);
        } catch (DuplicateKeyException e) {
            throw new CidasBusinessException(ErrorCode.DUPLICATE, "该账号已是课程成员");
        }
    }

    /** 名单里无账号的学生：建未激活账号并回读主键（username 上有唯一约束）。 */
    public String createInactiveUser(String accountNo, String name) {
        try {
            mapper.insertInactiveUser(accountNo, name);
        } catch (DuplicateKeyException e) {
            throw new CidasBusinessException(ErrorCode.DUPLICATE, "账号已存在：" + accountNo);
        }
        return mapper.selectUserByAccount(accountNo).getUserId();
    }

    // ---------- 作业 ----------

    public List<AssignmentStats> listAssignments(String userIdHex, String courseIdHex, String status) {
        return mapper.selectAssignments(userIdHex, courseIdHex, status);
    }

    public AssignmentStats findAssignment(String assignmentIdHex) {
        return mapper.selectAssignmentById(assignmentIdHex);
    }

    public String createAssignment(AssignmentCreate cmd) {
        String idHex = Ids.hex16();
        mapper.insertAssignment(idHex, cmd.courseIdHex(), cmd.title(), cmd.description(), cmd.publishedByHex(),
                cmd.dueTime(), cmd.allowLate(), cmd.submissionFormat(), cmd.requireFormatCheck(),
                cmd.rubricJson(), cmd.status());
        return idHex;
    }

    public List<MemberAccount> listUnsubmitted(String assignmentIdHex) {
        return mapper.selectUnsubmittedMembers(assignmentIdHex);
    }

    /** 课程 TEACHER 成员（提交作业消息的收件人，2-5 生产者①）。 */
    public List<MemberAccount> listTeacherMembers(String courseIdHex) {
        return mapper.selectTeacherMembers(courseIdHex);
    }

    /** 临近截止扫描：PUBLISHED 且 due_time 在 [from,to] 窗口内的作业（2-5 生产者④）。 */
    public List<AssignmentStats> listDueSoonAssignments(OffsetDateTime from, OffsetDateTime to) {
        return mapper.selectDueSoonAssignments(from, to);
    }

    public List<Priority> listPriorities(String userIdHex, int limit) {
        return mapper.selectPriorityAssignments(userIdHex, limit);
    }

    public long countDueThisWeek(String userIdHex, OffsetDateTime from, OffsetDateTime to) {
        return mapper.countDueThisWeek(userIdHex, from, to);
    }

    public long countPendingGrade(String userIdHex) {
        return mapper.countPendingGrade(userIdHex);
    }

    public long countFormatIssues(String userIdHex) {
        return mapper.countFormatIssues(userIdHex);
    }

    // ---------- 学生作业与提交 ----------

    public List<StudentAssignment> listStudentAssignments(String studentIdHex, String courseIdHex) {
        return mapper.selectStudentAssignments(studentIdHex, courseIdHex);
    }

    public OwnedDocument findOwnedReadyDocument(String studentIdHex, String documentIdHex) {
        return mapper.selectOwnedReadyDocument(studentIdHex, documentIdHex);
    }

    /** 下一次次序号：现有提交数 + 1，与 {@code uk_submission_1} 配合防并发重复。 */
    public long nextAttempt(String assignmentIdHex, String studentIdHex) {
        return mapper.countSubmissions(assignmentIdHex, studentIdHex) + 1;
    }

    public String createSubmission(String assignmentIdHex, String studentIdHex, String documentIdHex,
                                   String documentVersionIdHex, String formatCheckIdHex,
                                   long attemptNo, String status) {
        try {
            mapper.insertSubmission(assignmentIdHex, studentIdHex, documentIdHex, documentVersionIdHex,
                    formatCheckIdHex, attemptNo, status);
        } catch (DuplicateKeyException e) {
            throw new CidasBusinessException(ErrorCode.DUPLICATE, "同一作业的同一提交次数已存在，请重试");
        }
        return mapper.selectSubmissionId(assignmentIdHex, studentIdHex, attemptNo);
    }

    /** 批改入口按主键取单条提交（只取归属列）；课程范围校验在应用服务层，越权 40301。 */
    public Submission findSubmissionById(String submissionIdHex) {
        return mapper.selectSubmissionById(submissionIdHex);
    }

    public List<Submission> listSubmissions(String userIdHex, String assignmentIdHex, String status) {
        return mapper.selectSubmissions(userIdHex, assignmentIdHex, status);
    }

    public void gradeSubmission(String submissionIdHex, BigDecimal score, String feedback,
                                String scoreJson, String status) {
        mapper.updateSubmissionGrade(submissionIdHex, score, feedback, scoreJson, status);
    }

    /** 作业新建入参（含状态：DRAFT 存草稿、PUBLISHED 直接发布）。 */
    public record AssignmentCreate(String courseIdHex, String title, String description, String publishedByHex,
                                   OffsetDateTime dueTime, String allowLate, String submissionFormat,
                                   String requireFormatCheck, String rubricJson, String status) {
    }
}
