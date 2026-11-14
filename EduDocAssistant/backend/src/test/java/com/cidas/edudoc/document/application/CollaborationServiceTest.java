package com.cidas.edudoc.document.application;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.application.impl.CollaborationServiceImpl;
import com.cidas.edudoc.document.infrastructure.CollaboratorRow;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentCollaboratorRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 协作者：owner 才能增删；owner/协作者可读；在线心跳 15 秒窗口。 */
class CollaborationServiceTest {

    private static final String DOC = "AA0000000000000000000000000000A1";
    private static final String VER = "BB0000000000000000000000000000B1";
    private static final String USER2 = "CC0000000000000000000000000000C1";
    private static final String COLLAB = "DD0000000000000000000000000000D1";

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentCollaboratorRepository collaboratorRepository = mock(DocumentCollaboratorRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final CollaborationService service = new CollaborationServiceImpl(documentRepository,
            collaboratorRepository, auditService);

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    private static void login(String userId) {
        AuthContext.set(new UserPrincipal(userId, "owner", "张同学", Set.of("STUDENT"),
                Set.of("DOCUMENT_READ"), Set.of()));
    }

    private static Document doc(String owner) {
        return new Document(DOC, null, owner, "报告", "REPORT", "READY", VER);
    }

    @Test
    void ownerAddsCollaboratorWithSession() {
        login("U1");
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(doc("U1")));
        CollaboratorRow user = new CollaboratorRow();
        user.setUserId(USER2);
        user.setUsername("stu2");
        user.setDisplayName("陈同学");
        when(collaboratorRepository.findUserByUsername("stu2")).thenReturn(user);
        when(collaboratorRepository.ensureSession(DOC, VER, "U1")).thenReturn("S1");
        CollaboratorRow member = new CollaboratorRow();
        member.setId("M1");
        member.setUserId(USER2);
        member.setUsername("stu2");
        member.setDisplayName("陈同学");
        member.setPermission("COMMENT");
        member.setJoinedAt(OffsetDateTime.now());
        when(collaboratorRepository.findMember("S1", USER2)).thenReturn(member);

        CollaborationService.CollaboratorView view = service.addCollaborator(DOC,
                new CollaborationService.AddCollaboratorCommand("stu2", null));

        assertEquals("M1", view.id());
        assertEquals("COMMENT", view.permission());
        verify(collaboratorRepository).addMember("S1", USER2, "COMMENT");
        verify(auditService).record(eq("COLLABORATOR_ADDED"), eq("DOCUMENT"), eq(DOC), eq(DOC),
                eq("200"), any());
    }

    @Test
    void nonOwnerCannotAddCollaborator() {
        login("U2");
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(doc("U1")));
        when(collaboratorRepository.isActiveMember(DOC, "U2")).thenReturn(false);

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.addCollaborator(DOC, new CollaborationService.AddCollaboratorCommand("stu3", "COMMENT")));
        assertEquals(ErrorCode.NOT_FOUND, e.errorCode());
        verify(collaboratorRepository, never()).ensureSession(any(), any(), any());
    }

    @Test
    void collaboratorCanReadAndAppearInPresence() {
        login(COLLAB);
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(doc("U1")));
        when(collaboratorRepository.isActiveMember(DOC, COLLAB)).thenReturn(true);

        service.requireReadable(DOC);
        service.heartbeat(DOC);

        List<CollaborationService.PresenceView> list = service.presence(DOC);
        assertEquals(1, list.size());
        assertEquals(COLLAB, list.get(0).userId());
        assertEquals("STUDENT", list.get(0).role());
    }
}