package com.cidas.edudoc.document.application;

import java.time.OffsetDateTime;
import java.util.List;

/** 文档共享链接：创建、列出、撤销与免登录只读访问（06 共享切片）。 */
public interface ShareService {

    /** 创建链接共享；权限仅 VIEW（只读）/ COMMENT（可批注）。 */
    CreatedShare create(String documentId, CreateCommand command);

    /** 列出该文档的链接与状态（仅 owner）。 */
    List<ShareView> list(String documentId);

    /** 撤销链接（仅 owner；重复撤销幂等）。 */
    void revoke(String shareId);

    /** 免登录只读：只返回当前版本文本与标题，不暴露文件对象。 */
    PublicShare publicView(String token);

    record CreateCommand(Integer expiresInDays, String permission) {
    }

    record CreatedShare(String shareId, String token, String path, String permission,
                        OffsetDateTime expiresAt) {
    }

    record ShareView(String id, String token, String path, String permission, String status,
                     OffsetDateTime expiresAt, OffsetDateTime createTime) {
    }

    record PublicShare(String title, Integer versionNo, String permission, String text) {
    }
}