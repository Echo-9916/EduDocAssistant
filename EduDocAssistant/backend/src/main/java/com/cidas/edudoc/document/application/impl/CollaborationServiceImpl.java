package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.application.CollaborationService;
import com.cidas.edudoc.document.infrastructure.CollaboratorRow;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentCollaboratorRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协作者与在线状态。
 *
 * <p>协作成员复用 edudoc_collab_session + edudoc_collab_member：每个文档懒建一个会话，
 * 成员权限写 collab_member.permission_level。在线状态不落库，见 {@link #heartbeat}。
 */
@Service
@RequiredArgsConstructor
public class CollaborationServiceImpl implements CollaborationService {

    private static final Set<String> PERMISSIONS = Set.of("VIEW", "COMMENT", "EDIT");
    private static final long PRESENCE_TTL_MILLIS = 15_000L;

    private final DocumentRepository documentRepository;
    private final DocumentCollaboratorRepository collaboratorRepository;
    private final AuditService auditService;

    // ponytail: 单机内存，多节点需换 Redis
    private final Map<String, Map<String, Presence>> presence = new ConcurrentHashMap<>();

    @Override
    public List<CollaboratorView> listCollaborators(String documentId) {
        Document doc = requireReadableDoc(documentId);
        return viewMembers(doc.getId());
    }

    @Override
    @Transactional
    public CollaboratorView addCollaborator(String documentId, AddCollaboratorCommand command) {
        Document doc = requireOwned(documentId);
        String username = command == null || command.username() == null ? "" : command.username().trim();
        if (username.isEmpty()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "协作者账号不能为空");
        }
        String permission = command.permission() == null ? "COMMENT" : command.permission().trim().toUpperCase();
        if (!PERMISSIONS.contains(permission)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "权限仅支持 VIEW、COMMENT、EDIT");
        }
        CollaboratorRow user = collaboratorRepository.findUserByUsername(username);
        if (user == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        if (doc.getCurrentVersionId() == null) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "文档尚未生成版本，无法添加协作者");
        }
        String ownerId = AuthContext.require().userId();
        String sessionId = collaboratorRepository.ensureSession(doc.getId(), doc.getCurrentVersionId(), ownerId);
        collaboratorRepository.addMember(sessionId, user.getUserId(), permission);
        auditService.record("COLLABORATOR_ADDED", "DOCUMENT", doc.getId(), doc.getId(), "200",
                Map.of("userId", user.getUserId(), "permission", permission));
        return toView(collaboratorRepository.findMember(sessionId, user.getUserId()));
    }

    @Override
    @Transactional
    public void removeCollaborator(String documentId, String userId) {
        Document doc = requireOwned(documentId);
        if (!Ids.isHex16(userId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "用户 ID 格式不合法");
        }
        String sessionId = collaboratorRepository.findSessionId(doc.getId());
        if (sessionId == null || collaboratorRepository.removeMember(sessionId, userId) == 0) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "协作者不存在");
        }
        auditService.record("COLLABORATOR_REMOVED", "DOCUMENT", doc.getId(), doc.getId(), "200",
                Map.of("userId", userId));
    }

    @Override
    public boolean isCollaborator(String documentId, String userId) {
        return Ids.isHex16(documentId) && Ids.isHex16(userId)
                && collaboratorRepository.isActiveMember(documentId, userId);
    }

    @Override
    public void requireReadable(String documentId) {
        requireReadableDoc(documentId);
    }

    @Override
    public void heartbeat(String documentId) {
        UserPrincipal principal = AuthContext.require();
        requireReadableDoc(documentId);
        long now = System.currentTimeMillis();
        Map<String, Presence> byDocument = presence.computeIfAbsent(documentId, k -> new ConcurrentHashMap<>());
        byDocument.put(principal.userId(), new Presence(principal.userId(), principal.displayName(),
                principal.roles().isEmpty() ? null : principal.roles().iterator().next(), now));
        byDocument.entrySet().removeIf(e -> now - e.getValue().lastSeenMillis() > PRESENCE_TTL_MILLIS);
    }

    @Override
    public List<PresenceView> presence(String documentId) {
        requireReadableDoc(documentId);
        long now = System.currentTimeMillis();
        Map<String, Presence> byDocument = presence.getOrDefault(documentId, Map.of());
        return byDocument.values().stream()
                .filter(p -> now - p.lastSeenMillis() <= PRESENCE_TTL_MILLIS)
                .sorted(Comparator.comparingLong(Presence::lastSeenMillis).reversed())
                .map(p -> new PresenceView(p.userId(), p.displayName(), p.role(),
                        OffsetDateTime.now().minusNanos((now - p.lastSeenMillis()) * 1_000_000L)))
                .toList();
    }

    private List<CollaboratorView> viewMembers(String documentId) {
        String sessionId = collaboratorRepository.findSessionId(documentId);
        return sessionId == null ? List.of()
                : collaboratorRepository.listMembers(sessionId).stream()
                        .map(CollaborationServiceImpl::toView).toList();
    }

    private static CollaboratorView toView(CollaboratorRow row) {
        return new CollaboratorView(row.getId(), row.getUserId(), row.getUsername(), row.getDisplayName(),
                row.getPermission(), row.getJoinedAt() == null ? null : row.getJoinedAt().toString());
    }

    /** owner 或活跃协作者可读；跨文档/未授权一律 40401。 */
    private Document requireReadableDoc(String documentId) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        String userId = AuthContext.require().userId();
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        if ("DELETED".equals(doc.getStatus())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        if (!userId.equalsIgnoreCase(doc.getOwnerId()) && !isCollaborator(documentId, userId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }

    private Document requireOwned(String documentId) {
        Document doc = requireReadableDoc(documentId);
        if (!AuthContext.require().userId().equalsIgnoreCase(doc.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }

    private record Presence(String userId, String displayName, String role, long lastSeenMillis) {
    }
}