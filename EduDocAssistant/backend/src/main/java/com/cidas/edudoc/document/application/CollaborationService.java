package com.cidas.edudoc.document.application;

import java.time.OffsetDateTime;
import java.util.List;

/** 协作者、在线状态与文档读权限（owner 或协作者）。 */
public interface CollaborationService {

    List<CollaboratorView> listCollaborators(String documentId);

    CollaboratorView addCollaborator(String documentId, AddCollaboratorCommand command);

    void removeCollaborator(String documentId, String userId);

    /** 是否为该文档的活跃协作者（不含 owner）。 */
    boolean isCollaborator(String documentId, String userId);

    /** 读权限校验：owner 或协作者，否则 40401。 */
    void requireReadable(String documentId);

    /** 在线心跳：进程内 TTL Map，15 秒内视为在线。 */
    void heartbeat(String documentId);

    List<PresenceView> presence(String documentId);

    record AddCollaboratorCommand(String username, String permission) {
    }

    record CollaboratorView(String id, String userId, String username, String displayName,
                            String permission, String joinedAt) {
    }

    record PresenceView(String userId, String displayName, String role, OffsetDateTime lastSeen) {
    }
}