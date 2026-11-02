package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.infrastructure.Course;
import com.cidas.edudoc.access.infrastructure.CourseRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.application.UploadSessionService.NewUpload;
import com.cidas.edudoc.document.application.UploadSessionService.SessionTicket;
import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.document.infrastructure.UploadSessionRepository;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter.UploadCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadSessionServiceImplTest {

    private static final UploadCredentials CREDENTIALS =
            new UploadCredentials("AK", "SK", "TOKEN", "2026-11-02T12:00:00Z", "b", "cn-beijing", "endpoint");

    private final UploadSessionRepository sessionRepository = mock(UploadSessionRepository.class);
    private final OssStorageAdapter ossStorageAdapter = mock(OssStorageAdapter.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final OssProperties properties = new OssProperties("https://oss-cn-beijing.aliyuncs.com",
            "cn-beijing", "b", "ak", "sk", "acs:ram::1:role/edudoc-upload",
            "sts.cn-beijing.aliyuncs.com", "edudoc/uploads", 120, 900);
    private final com.cidas.edudoc.document.application.DocumentProvisioningService provisioningService =
            mock(com.cidas.edudoc.document.application.DocumentProvisioningService.class);
    private final UploadSessionServiceImpl service = new UploadSessionServiceImpl(
            sessionRepository, ossStorageAdapter, courseRepository, properties, provisioningService);

    @BeforeEach
    void setUp() {
        when(ossStorageAdapter.issueUploadCredentials(anyString(), anyString())).thenReturn(CREDENTIALS);
    }

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    private static NewUpload upload(String courseId, String fileName, long size, String mimeType) {
        return new NewUpload(courseId, "  期末论文  ", fileName, size, mimeType, "THESIS");
    }

    @Test
    void studentUploadWithoutCourseCreatesOpenSessionAndSanitizedKey() {
        AuthContext.set(new UserPrincipal("U1", "student", null, Set.of("STUDENT"), Set.of(), Set.of()));

        SessionTicket ticket = service.createSession(upload(null, "../../ evil name.docx", 1024,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

        ArgumentCaptor<UploadSession> saved = ArgumentCaptor.forClass(UploadSession.class);
        verify(sessionRepository).save(saved.capture());
        UploadSession session = saved.getValue();
        assertEquals(UploadSession.Status.OPEN, session.status());
        assertEquals("期末论文", session.title());
        assertNull(session.courseId());
        String expectedPrefix = "edudoc/uploads/" + YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
                + "/U1/" + session.id() + "-_evil_name.docx";
        assertEquals(expectedPrefix, session.objectKey());
        assertEquals(CREDENTIALS, ticket.credentials());
    }

    @Test
    void blankMimeTypeFallsBackToExtensionCheck() {
        AuthContext.set(new UserPrincipal("U1", "student", null, Set.of("STUDENT"), Set.of(), Set.of()));

        SessionTicket ticket = service.createSession(upload(null, "a.pdf", 1024, " "));

        assertEquals("a.pdf", ticket.session().fileName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"report.txt", "report.exe", "noext"})
    void rejectsUnsupportedExtension(String fileName) {
        AuthContext.set(new UserPrincipal("U1", "student", null, Set.of("STUDENT"), Set.of(), Set.of()));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.createSession(upload(null, fileName, 1024, "application/pdf")));
        assertEquals(ErrorCode.UNSUPPORTED_MEDIA_TYPE, e.errorCode());
    }

    @Test
    void rejectsMismatchedMimeType() {
        AuthContext.set(new UserPrincipal("U1", "student", null, Set.of("STUDENT"), Set.of(), Set.of()));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.createSession(upload(null, "a.pdf", 1024, "text/plain")));
        assertEquals(ErrorCode.UNSUPPORTED_MEDIA_TYPE, e.errorCode());
    }

    @Test
    void rejectsOversizedFile() {
        AuthContext.set(new UserPrincipal("U1", "student", null, Set.of("STUDENT"), Set.of(), Set.of()));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.createSession(upload(null, "a.pdf", 20L * 1024 * 1024 + 1, "application/pdf")));
        assertEquals(ErrorCode.FILE_TOO_LARGE, e.errorCode());
    }

    @Test
    void teacherMustBindCourse() {
        AuthContext.set(new UserPrincipal("U2", "teacher", null, Set.of("TEACHER"), Set.of(), Set.of("C1")));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.createSession(upload(null, "a.pdf", 1024, "application/pdf")));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }

    @Test
    void rejectsNonMemberCourse() {
        AuthContext.set(new UserPrincipal("U1", "student", null, Set.of("STUDENT"), Set.of(), Set.of()));
        when(courseRepository.listByUser("U1")).thenReturn(List.of());

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.createSession(upload("C9", "a.pdf", 1024, "application/pdf")));
        assertEquals(ErrorCode.FORBIDDEN, e.errorCode());
    }

    @Test
    void teacherWithStudentMemberRoleIsRejected() {
        AuthContext.set(new UserPrincipal("U2", "teacher", null, Set.of("TEACHER"), Set.of(), Set.of("C1")));
        when(courseRepository.listByUser("U2")).thenReturn(
                List.of(new Course("C1", "CS101", "计算机导论", "2026-FALL", "STUDENT")));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.createSession(upload("C1", "a.pdf", 1024, "application/pdf")));
        assertEquals(ErrorCode.FORBIDDEN, e.errorCode());
    }

    @Test
    void teacherWithAssistantRoleIsAccepted() {
        AuthContext.set(new UserPrincipal("U2", "teacher", null, Set.of("TEACHER"), Set.of(), Set.of("C1")));
        when(courseRepository.listByUser("U2")).thenReturn(
                List.of(new Course("C1", "CS101", "计算机导论", "2026-FALL", "ASSISTANT")));

        SessionTicket ticket = service.createSession(upload("C1", "a.pdf", 1024, "application/pdf"));

        assertEquals("C1", ticket.session().courseId());
        verify(ossStorageAdapter).issueUploadCredentials(eq(ticket.session().objectKey()), anyString());
    }

    @Test
    void rejectsBlankTitle() {
        AuthContext.set(new UserPrincipal("U1", "student", null, Set.of("STUDENT"), Set.of(), Set.of()));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.createSession(new NewUpload(null, "   ", "a.pdf", 1024, "application/pdf", "THESIS")));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }
}
