package com.cidas.edudoc.coursework.infrastructure;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * coursework 域的查询投影行（MyBatis resultType 用 {@code CourseworkRow$XxxRow} 嵌套类名）。
 *
 * <p>集中一个文件是因为它们只是列的搬运工：没有行为、不参与写路径、跨端点复用。
 * 主键一律 RAWTOHEX 后的 32 位大写十六进制串（见 {@code common/persistence/Ids}）。
 */
public final class CourseworkRow {

    private CourseworkRow() {
    }

    /** 教师课程列表行（含成员规模、关联模板与本周截止数）。 */
    @Data
    public static class TeacherCourse {
        private String id;
        private String courseCode;
        private String courseName;
        private String termCode;
        private String ownerName;
        private long groups;
        private long students;
        private String templateName;
        private String status;
        private long dueWeek;
    }

    /** 教师作业列表行（带提交统计）。 */
    @Data
    public static class AssignmentStats {
        private String id;
        private String courseId;
        private String courseName;
        private String title;
        private String description;
        private OffsetDateTime dueTime;
        private String allowLate;
        private String submissionFormat;
        private String requireFormatCheck;
        private String rubricJson;
        private String status;
        private long expected;
        private long submitted;
        private long overdue;
        private long graded;
        private long formatIssues;
    }

    /** 未提交学生名单行（提醒入口，投递随消息切片）。 */
    @Data
    public static class MemberAccount {
        private String userId;
        private String accountNo;
        private String name;
    }

    /** 学生作业列表行（作业 + 本人最新提交 + 文档与格式结论）。 */
    @Data
    public static class StudentAssignment {
        private String assignmentId;
        private String courseName;
        private String title;
        private String description;
        private OffsetDateTime dueTime;
        private String allowLate;
        private String submissionFormat;
        private String requireFormatCheck;
        private String assignmentStatus;
        private String submissionId;
        private String submissionStatus;
        private Integer attemptNo;
        private OffsetDateTime submittedAt;
        private BigDecimal score;
        private String feedback;
        private String scoreJson;
        private String documentId;
        private String documentTitle;
        private String documentVersionId;
        private Integer versionNo;
        /** 格式校验结论原始计数：-1 表示未跑校验或无该列，PASS/WARN/BLOCK 由应用层派生。 */
        private long blockingCount;
        private long warningCount;
    }

    /** 提交（批改视角）行。 */
    @Data
    public static class Submission {
        private String id;
        private String assignmentId;
        private String assignmentTitle;
        private String courseName;
        private String studentId;
        private String studentName;
        private String accountNo;
        private String documentId;
        private String documentTitle;
        private Integer versionNo;
        /** 格式校验结论原始计数：-1 表示未跑校验或无该列，PASS/WARN/BLOCK 由应用层派生。 */
        private long blockingCount;
        private long warningCount;
        private String status;
        private BigDecimal score;
        private String feedback;
        private String scoreJson;
        private Integer attemptNo;
        private OffsetDateTime submittedAt;
    }

    /** 待提交文档候选（学生本人 READY 文档 + 最新未删版本）。 */
    @Data
    public static class OwnedDocument {
        private String documentId;
        private String title;
        private String versionId;
        private Integer versionNo;
        private String status;
    }

    /** 工作台优先处理项：按截止时间升序的作业 + 提交缺口。 */
    @Data
    public static class Priority {
        private String assignmentId;
        private String courseName;
        private String title;
        private OffsetDateTime dueTime;
        private long expected;
        private long submitted;
        private long graded;
    }

    /** 课程成员导入的单行结果。 */
    @Data
    public static class ImportRow {
        private String accountNo;
        private String name;
        private String group;
    }
}
