package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.infrastructure.Course;
import com.cidas.edudoc.access.infrastructure.CourseRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.application.UploadSessionService;
import com.cidas.edudoc.document.domain.DocumentType;
import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.document.infrastructure.UploadSessionRepository;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadSessionServiceImpl implements UploadSessionService {

    private static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("doc", "docx", "pdf");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/pdf");
    private static final Set<String> TEACHING_MEMBER_ROLES = Set.of("TEACHER", "ASSISTANT");
    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyyMM");

    private final UploadSessionRepository sessionRepository;
    private final OssStorageAdapter ossStorageAdapter;
    private final CourseRepository courseRepository;
    private final OssProperties ossProperties;

    @Override
    public SessionTicket createSession(NewUpload upload) {
        UserPrincipal principal = AuthContext.require();
        String title = requireTitle(upload.title());
        DocumentType documentType = requireDocumentType(upload.documentType());
        requireSupportedFile(upload);
        String courseId = requireCourse(principal, upload.courseId());

        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String objectKey = buildObjectKey(principal.userId(), sessionId, upload.fileName());
        UploadSession session = new UploadSession(sessionId, principal.userId(), courseId, title,
                documentType, upload.fileName(), upload.sizeBytes(), upload.mimeType(), objectKey,
                UploadSession.Status.OPEN, null, null, Instant.now());
        UploadSessionService.SessionTicket ticket = new SessionTicket(session,
                ossStorageAdapter.issueUploadCredentials(objectKey, "edudoc-" + sessionId));
        sessionRepository.save(session);
        return ticket;
    }

    private String requireTitle(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty() || trimmed.length() > 200) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "标题须为 1–200 个字符");
        }
        return trimmed;
    }

    private DocumentType requireDocumentType(String code) {
        DocumentType type = DocumentType.from(code);
        if (type == null) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档类型不合法");
        }
        return type;
    }

    private void requireSupportedFile(NewUpload upload) {
        if (upload.sizeBytes() <= 0) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文件大小不合法");
        }
        if (upload.sizeBytes() > MAX_SIZE_BYTES) {
            throw new CidasBusinessException(ErrorCode.FILE_TOO_LARGE, "文件超过 20MB 上限");
        }
        String extension = extensionOf(upload.fileName());
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new CidasBusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "仅支持 Word(.doc/.docx) 与 PDF 文件");
        }
        // mimeType 提供时必须与扩展名同类（双重校验）；个别浏览器对 .doc 送空值，此时以扩展名为准
        if (upload.mimeType() != null && !upload.mimeType().isBlank()
                && !ALLOWED_MIME_TYPES.contains(upload.mimeType().toLowerCase(Locale.ROOT))) {
            throw new CidasBusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "文件类型与扩展名不符");
        }
    }

    private String extensionOf(String fileName) {
        String name = fileName == null ? "" : fileName;
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? null
                : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String requireCourse(UserPrincipal principal, String courseId) {
        boolean isTeacher = principal.roles().contains("TEACHER");
        if (courseId == null || courseId.isBlank()) {
            if (isTeacher) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "教师上传须绑定课程");
            }
            return null;
        }
        // ponytail: teacher_id 负责人但无成员记录的课程待课程管理模块补充，先按成员关系判定
        Course course = courseRepository.listByUser(principal.userId()).stream()
                .filter(c -> c.getId().equalsIgnoreCase(courseId))
                .findFirst()
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.FORBIDDEN, "非该课程成员，禁止绑定"));
        if (isTeacher && !TEACHING_MEMBER_ROLES.contains(course.getMemberRole())) {
            throw new CidasBusinessException(ErrorCode.FORBIDDEN, "课程角色不允许向该课程上传");
        }
        return courseId;
    }

    private String buildObjectKey(String userIdHex, String sessionId, String fileName) {
        List<String> parts = List.of(
                ossProperties.objectKeyPrefix(),
                YearMonth.now().format(YYYY_MM),
                userIdHex,
                sessionId + "-" + sanitizeFileName(fileName));
        return String.join("/", parts);
    }

    /** 只保留安全字符（概要 §7.1 防路径注入），限长 100。 */
    private String sanitizeFileName(String fileName) {
        String base = (fileName == null ? "file" : fileName)
                .replaceAll(".*[\\\\/]", "")
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isBlank() || base.equals("_")) {
            base = "file";
        }
        return base.length() > 100 ? base.substring(0, 100) : base;
    }
}
