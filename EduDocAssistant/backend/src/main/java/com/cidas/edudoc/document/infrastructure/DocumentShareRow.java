package com.cidas.edudoc.document.infrastructure;

import lombok.Data;

import java.time.OffsetDateTime;

/** 共享链接行（共享表 + 授权表的展示投影）；时间保留 OffsetDateTime，应用层统一转 ISO。 */
@Data
public class DocumentShareRow {

    private String id;
    private String documentId;
    private String ownerId;
    private String shareScope;
    /** 复用 link_token_hash 列；当前存随机 token 本体，列表需要回显链接（见 ShareServiceImpl 注释）。 */
    private String linkToken;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createTime;
    private String status;
    private String permission;
}