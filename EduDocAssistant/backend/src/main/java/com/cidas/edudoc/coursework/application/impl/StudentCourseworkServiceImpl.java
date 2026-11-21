package com.cidas.edudoc.coursework.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.coursework.application.StudentCourseworkService;
import com.cidas.edudoc.coursework.domain.CourseworkRules;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRepository;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.AssignmentAttachment;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.AssignmentStats;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.MemberAccount;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.OwnedDocument;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.StudentAssignment;
import com.cidas.edudoc.document.application.DocumentProvisioningService.CreatedDocument;
import com.cidas.edudoc.document.domain.DocumentType;
import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.message.application.NotificationEvents;
import com.cidas.edudoc.template.application.FormatCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudentCourseworkServiceImpl implements StudentCourseworkService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final long MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024;

    private final CourseworkRepository courseworkRepository;
    private final AuditService auditService;
    private final OutboxEventRepository outboxEventRepository;
    private final FormatCheckService formatCheckService;
    private final OssStorageAdapter ossStorageAdapter;
    private final OssProperties ossProperties;
    private final CourseworkAttachmentTxWriter attachmentTxWriter;

    @Override
    public PageResult<StudentAssignmentView> listAssignments(ListQuery query) {
        String studentId = AuthContext.require().userId();
        // HEXTORAW 前在入口拒绝非 hex（同 DocumentQueryServiceImpl 的 1103 审查 S2 口径）
        String courseId = blankToNull(query.courseId());
        if (courseId != null && !Ids.isHex16(courseId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "courseId 格式不合法");
        }
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int pageSize = query.pageSize() == null || query.pageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.pageSize(), MAX_PAGE_SIZE);
        OffsetDateTime now = OffsetDateTime.now();
        String statusFilter = blankToNull(query.status());

        List<StudentAssignmentView> all = courseworkRepository.listStudentAssignments(studentId, courseId).stream()
                .map(row -> toView(row, now))
                // mapper 不做派生状态过滤（PENDING/OVERDUE 由截止时间现算），只能在这里滤
                .filter(view -> statusFilter == null || statusFilter.equals(view.status()))
                .toList();
        // 内存分页：单学生可见作业按课程×学期计只有几十条，SQL 分页留到数据量真上来再加
        // ponytail: 内存分页上限=单学生全部作业行数，量级破千再下沉 SQL（OFFSET/FETCH 见 selectTeacherCourses）
        int from = (int) Math.min((long) (page - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return new PageResult<>(all.subList(from, to), all.size(), page, pageSize);
    }

    @Override
    @Transactional
    public SubmissionResult submit(String assignmentId, String documentId) {
        if (!Ids.isHex16(assignmentId) || !Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "ID 格式不合法");
        }
        UserPrincipal principal = AuthContext.require();
        String studentId = principal.userId();
        AssignmentStats assignment = courseworkRepository.findAssignment(assignmentId);
        if (assignment == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        // 鉴权第 4 步（课程范围）：只能提交本人所在课程的作业，否则任意学生可对任意已发布作业提交
        if (!courseworkRepository.isCourseMember(assignment.getCourseId(), studentId)) {
            throw new CidasBusinessException(ErrorCode.FORBIDDEN, "未选修该课程，不能提交此作业");
        }
        // 草稿作业不可提交：列表 SQL 已过滤 DRAFT，但提交端点必须自己拦住（实测 8081 曾直接 201）
        if ("DRAFT".equals(assignment.getStatus())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "作业尚未发布，不能提交");
        }
        // 本人 owner + READY 一条 SQL 同时校验，null 无法区分「无权」与「未解析完成」，统一一句话
        OwnedDocument document = courseworkRepository.findOwnedReadyDocument(studentId, documentId);
        if (document == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在、无权访问或未解析完成");
        }
        CourseworkRules.SubmissionOutcome outcome = CourseworkRules.submissionOutcome(
                assignment.getStatus(), assignment.getDueTime(),
                "Y".equals(assignment.getAllowLate()), OffsetDateTime.now());
        long attemptNo = courseworkRepository.nextAttempt(assignmentId, studentId);
        // 方案 A：复用该文档版本最近一次已完成的格式校验（学生需在预览页先跑过校验）；
        // 没有就跑不到结果，提交照常成功，教师侧显示「未校验」。
        String formatCheckId = formatCheckService.latestCompletedCheckId(document.getVersionId());
        String submissionId = courseworkRepository.createSubmission(assignmentId, studentId, documentId,
                document.getVersionId(), formatCheckId, attemptNo, outcome.name());
        auditService.record("SUBMIT_ASSIGNMENT", "SUBMISSION", submissionId, documentId, "200",
                Map.of("assignmentId", assignmentId, "attemptNo", attemptNo, "status", outcome.name()));
        // 2-5 生产者①（设计 §4.1）：提交 → 该课 TEACHER 成员收 SUBMISSION 消息，
        // publish 与业务写、审计同事务；去重聚合 = submissionId（重交是新提交，教师会再收一封）
        List<String> teachers = courseworkRepository.listTeacherMembers(assignment.getCourseId()).stream()
                .map(MemberAccount::getUserId)
                .toList();
        if (!teachers.isEmpty()) {
            outboxEventRepository.publish("SUBMISSION", submissionId, NotificationEvents.EVENT_TYPE,
                    NotificationEvents.payload(NotificationEvents.TYPE_SUBMISSION, submissionId, null,
                            "ASSIGNMENT", assignmentId, studentId, teachers,
                            "作业提交通知",
                            "学生 " + principal.displayName() + " 已提交《" + assignment.getTitle()
                                    + "》（第 " + attemptNo + " 次），请及时批改。"));
        }
        return new SubmissionResult(submissionId, attemptNo, outcome.name());
    }

    // ---------- 作业附件 ----------

    @Override
    public List<AttachmentView> listAttachments(String assignmentId) {
        AssignmentStats assignment = requireVisibleAssignment(assignmentId);
        return courseworkRepository.listAttachments(assignment.getId()).stream()
                .map(row -> new AttachmentView(row.getId(), row.getFileName(), row.getMimeType(),
                        row.getSizeBytes()))
                .toList();
    }

    @Override
    public AttachmentFile downloadAttachment(String assignmentId, String attachmentId) {
        requireVisibleAssignment(assignmentId);
        if (!Ids.isHex16(attachmentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "附件 ID 格式不合法");
        }
        AssignmentAttachment attachment = courseworkRepository.findAttachment(assignmentId, attachmentId);
        if (attachment == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        byte[] bytes = ossStorageAdapter.readBytes(attachment.getObjectKey(), MAX_ATTACHMENT_BYTES);
        return new AttachmentFile(attachment.getFileName(), attachment.getMimeType(), bytes);
    }

    /**
     * 基于附件新建学生自己的文档：复制附件字节为新的 OSS 对象，走上传建档链路并绑定作业模板，
     * 学生随后可在预览页编辑、按模板校验并作为提交文档。
     */
    @Override
    @Transactional
    public CreatedDocumentView createDocumentFromAttachment(String assignmentId, String attachmentId) {
        UserPrincipal principal = AuthContext.require();
        AssignmentStats assignment = requireVisibleAssignment(assignmentId);
        if (!Ids.isHex16(attachmentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "附件 ID 格式不合法");
        }
        AssignmentAttachment attachment = courseworkRepository.findAttachment(assignmentId, attachmentId);
        if (attachment == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        if (!DOCX_MIME.equals(attachment.getMimeType())) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "附件不是 Word 文档，无法直接编辑");
        }
        byte[] bytes = ossStorageAdapter.readBytes(attachment.getObjectKey(), MAX_ATTACHMENT_BYTES);
        if (bytes.length < 2 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "附件不是有效的 .docx");
        }
        String title = attachment.getFileName().replaceFirst("\\.[^.]+$", "").trim();
        if (title.isEmpty()) {
            title = assignment.getTitle();
        }
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }
        String sessionId = Ids.hex16();
        String objectKey = ossProperties.objectKeyPrefix() + "/generated/from-attachment/"
                + principal.userId() + "/" + sessionId + ".docx";
        var info = ossStorageAdapter.putBytes(objectKey, bytes, DOCX_MIME);
        UploadSession session = new UploadSession(sessionId, principal.userId(), assignment.getCourseId(),
                title, DocumentType.HOMEWORK, title + ".docx", bytes.length, DOCX_MIME, objectKey,
                UploadSession.Status.COMPLETING, null, null, Instant.now());
        CreatedDocument created;
        try {
            created = attachmentTxWriter.provision(session, info, sha256(bytes),
                    blankToNull(assignment.getTemplateVersionId()));
        } catch (DuplicateKeyException e) {
            throw new CidasBusinessException(ErrorCode.CONFLICT,
                    "同课程下已存在同名文档，请先重命名或删除旧文档后再试");
        }
        auditService.record("DOCUMENT_CREATED_FROM_ATTACHMENT", "ASSIGNMENT", assignmentId,
                created.documentId(), "200", Map.of("attachmentId", attachmentId));
        return new CreatedDocumentView(created.documentId(), created.versionId());
    }

    /** 学生可读作业：作业存在 + 本人是该课程成员（鉴权第 4 步课程范围）。 */
    private AssignmentStats requireVisibleAssignment(String assignmentId) {
        if (!Ids.isHex16(assignmentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "assignmentId 格式不合法");
        }
        String studentId = AuthContext.require().userId();
        AssignmentStats assignment = courseworkRepository.findAssignment(assignmentId);
        if (assignment == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        if (!courseworkRepository.isCourseMember(assignment.getCourseId(), studentId)) {
            throw new CidasBusinessException(ErrorCode.FORBIDDEN, "未选修该课程，不能查看附件");
        }
        return assignment;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static StudentAssignmentView toView(StudentAssignment row, OffsetDateTime now) {
        String status = CourseworkRules.studentStatus(row.getSubmissionStatus(), row.getDueTime(), now);
        return new StudentAssignmentView(
                row.getAssignmentId(), row.getCourseName(), row.getTitle(), row.getDescription(),
                iso(row.getDueTime()), remainText(status, row.getDueTime(), now), status,
                formatView(row.getBlockingCount(), row.getWarningCount()),
                row.getScore(), row.getFeedback(), row.getAttemptNo(), row.getDocumentTitle(),
                iso(row.getSubmittedAt()), row.getDocumentId(), row.getDocumentVersionId(),
                row.getVersionNo(), row.getSubmissionFormat(), row.getRequireFormatCheck(),
                row.getTemplateVersionId(), row.getTemplateName(), row.getTemplateVersionNo());
    }

    /** 格式结论派生：-1 表示未跑校验（NONE），阻塞优先于提醒。 */
    private static FormatView formatView(long blockingCount, long warningCount) {
        if (blockingCount < 0) {
            return new FormatView("NONE", "未做格式校验");
        }
        if (blockingCount > 0) {
            return new FormatView("BLOCK", blockingCount + " 项阻塞：需先修复再提交");
        }
        if (warningCount > 0) {
            return new FormatView("WARN", warningCount + " 项提醒：建议修复后再提交");
        }
        return new FormatView("PASS", "格式校验通过");
    }

    /** 剩余时间文案：只算整天数，不做时区换算（库列自带偏移，now 亦为 OffsetDateTime）。 */
    private static String remainText(String status, OffsetDateTime dueTime, OffsetDateTime now) {
        if (CourseworkRules.SUBMITTED.equals(status) || CourseworkRules.GRADED.equals(status) || dueTime == null) {
            return "";
        }
        if (CourseworkRules.OVERDUE.equals(status)) {
            long days = ChronoUnit.DAYS.between(dueTime, now);
            return days >= 1 ? "已逾期 " + days + " 天" : "已逾期不足 1 天";
        }
        long days = ChronoUnit.DAYS.between(now, dueTime);
        return days <= 0 ? "今天截止" : days + " 天后截止";
    }

    private static String iso(OffsetDateTime time) {
        return time == null ? null : time.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
