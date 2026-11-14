package com.cidas.edudoc.document.application;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.application.impl.ShareServiceImpl;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentChunk;
import com.cidas.edudoc.document.infrastructure.DocumentChunkRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentShareRepository;
import com.cidas.edudoc.document.infrastructure.DocumentShareRow;
import com.cidas.edudoc.document.infrastructure.DocumentViewRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 共享链接：创建校验、撤销归属、公开只读与过期/撤销口径。 */
class ShareServiceTest {

    private static final String DOC_ID = "AA0000000000000000000000000000A1";
    private static final String OWNER = "U1";
    private static final String SHARE_ID = "BB0000000000000000000000000000B1";

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
    private final DocumentShareRepository shareRepository = mock(DocumentShareRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final ShareService service = new ShareServiceImpl(documentRepository, chunkRepository,
            shareRepository, auditService);

    @BeforeEach
    void setUp() {
        AuthContext.set(new UserPrincipal(OWNER, "stu", null, Set.of("STUDENT"),
                Set.of("DOCUMENT_SHARE"), Set.of()));
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, null, OWNER, "报告", "REPORT", "READY", "V2")));
    }

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    @Test
    void createReturnsUnguessableTokenAndPath() {
        when(shareRepository.create(eq(DOC_ID), eq(OWNER), anyString(), any(), eq("VIEW")))
                .thenReturn("SHARE1");

        ShareService.CreatedShare created = service.create(DOC_ID,
                new ShareService.CreateCommand(7, "VIEW"));

        assertEquals("SHARE1", created.shareId());
        assertEquals("/s/" + created.token(), created.path());
        assertTrue(created.token().length() >= 40, "32 字节 Base64URL 至少 43 字符");
        verify(shareRepository).create(eq(DOC_ID), eq(OWNER), eq(created.token()), any(), eq("VIEW"));
        verify(auditService).record(eq("SHARE_CREATED"), eq("DOCUMENT"), eq(DOC_ID), eq(DOC_ID),
                eq("200"), any());
    }

    @Test
    void createRejectsBadPermissionAndExpiry() {
        assertCode(ErrorCode.BAD_REQUEST, () -> service.create(DOC_ID,
                new ShareService.CreateCommand(7, "EDIT")));
        assertCode(ErrorCode.BAD_REQUEST, () -> service.create(DOC_ID,
                new ShareService.CreateCommand(0, "VIEW")));
        verify(shareRepository, never()).create(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void createForeignDocumentIs40401() {
        Document other = new Document(DOC_ID, null, "OTHER", "t", "REPORT", "READY", "V2");
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(other));

        assertCode(ErrorCode.NOT_FOUND, () -> service.create(DOC_ID,
                new ShareService.CreateCommand(7, "VIEW")));
    }

    @Test
    void publicViewReturnsCurrentVersionTextAndTitle() {
        when(shareRepository.findByToken("TOK")).thenReturn(share("A", OffsetDateTime.now().plusDays(1)));
        when(documentRepository.detail(DOC_ID)).thenReturn(Optional.of(documentRow()));
        when(chunkRepository.selectAllByVersion("V2")).thenReturn(List.of(
                new DocumentChunk("C1", "V2", 1, null, 1, 0, 5, "第一段\n", 3, "h", "READY"),
                new DocumentChunk("C2", "V2", 2, null, 2, 6, 11, "第二段", 3, "h", "READY")));

        ShareService.PublicShare view = service.publicView("TOK");

        assertEquals("课程设计报告", view.title());
        assertEquals("第一段\n第二段", view.text());
        assertEquals("VIEW", view.permission());
    }

    @Test
    void revokedOrExpiredLinkIs40303() {
        when(shareRepository.findByToken("REVOKED")).thenReturn(share("R", null));
        assertCode(ErrorCode.SHARE_LINK_EXPIRED, () -> service.publicView("REVOKED"));

        DocumentShareRow expired = share("A", OffsetDateTime.now().minusMinutes(1));
        when(shareRepository.findByToken("EXPIRED")).thenReturn(expired);
        assertCode(ErrorCode.SHARE_LINK_EXPIRED, () -> service.publicView("EXPIRED"));
        verify(shareRepository).markExpired(SHARE_ID);
    }

    @Test
    void revokeForeignShareIs40401() {
        DocumentShareRow row = share("A", null);
        row.setOwnerId("OTHER");
        when(shareRepository.findById(SHARE_ID)).thenReturn(row);

        assertCode(ErrorCode.NOT_FOUND, () -> service.revoke(SHARE_ID));
        verify(shareRepository, never()).revoke(SHARE_ID);
    }

    private static DocumentShareRow share(String status, OffsetDateTime expiresAt) {
        DocumentShareRow row = new DocumentShareRow();
        row.setId(SHARE_ID);
        row.setDocumentId(DOC_ID);
        row.setOwnerId(OWNER);
        row.setLinkToken("TOK");
        row.setPermission("VIEW");
        row.setStatus(status);
        row.setExpiresAt(expiresAt);
        row.setCreateTime(OffsetDateTime.now());
        return row;
    }

    private static DocumentViewRow documentRow() {
        return new DocumentViewRow(DOC_ID, OWNER, null, null, "课程设计报告", "REPORT", "READY", null, null,
                null, "text/plain", 2, "V2", "V2", "h", 2, "2026-11-14T10:00:00",
                "2026-11-14T10:00:00", null, null);
    }

    private static void assertCode(ErrorCode expected, Runnable action) {
        CidasBusinessException e = assertThrows(CidasBusinessException.class, action::run);
        assertEquals(expected, e.errorCode());
    }
}