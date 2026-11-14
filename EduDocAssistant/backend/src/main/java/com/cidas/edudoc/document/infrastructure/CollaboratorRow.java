package com.cidas.edudoc.document.infrastructure;

import lombok.Data;

import java.time.OffsetDateTime;

/** 协作者行（collab_member + user 展示投影）。 */
@Data
public class CollaboratorRow {

    private String id;
    private String userId;
    private String username;
    private String displayName;
    private String permission;
    private String connectionStatus;
    private OffsetDateTime joinedAt;
}