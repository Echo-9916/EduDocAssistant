package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.document.application.ShareService;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentChunkRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentShareRepository;
import com.cidas.edudoc.document.infrastructure.DocumentShareRow;
import com.cidas.edudoc.document.infrastructure.DocumentViewRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 共享链接实现。
 *
 * <p>权限落在 edudoc_share_grant：链接没有真实主体，复用字典值 GROUP + principal_ref='LINK' 承载，
 * 不新增 DDL。token 存 link_token_hash 列（列名为历史命名）：列表需要回显可复制链接，故存 token 本体；
 * 随机 32 字节、URL-safe，唯一约束兜底。若后续要求防库泄露，改为 SHA-256 存储并把完整链接限定为创建时一次性展示。
 */
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> PERMISSIONS = Set.of("VIEW", "COMMENT");
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 365;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentShareRepository shareRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public CreatedShare create(String documentId, CreateCommand command) {
        Document doc = requireOwned(documentId);
        Integer days = command == null ? null : command.expiresInDays();
        if (days == null || days < MIN_DAYS || days > MAX_DAYS) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "有效期需为 1-365 天");
        }
        String permission = command.permission() == null ? "" : command.permission().trim().toUpperCase();
        if (!PERMISSIONS.contains(permission)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "权限仅支持 VIEW（只读）或 COMMENT（可批注）");
        }
        String token = newToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(days);
        String shareId = shareRepository.create(doc.getId(), AuthContext.require().userId(), token,
                expiresAt, permission);
        auditService.record("SHARE_CREATED", "DOCUMENT", doc.getId(), doc.getId(), "200",
                Map.of("shareId", shareId, "permission", permission, "expiresAt", expiresAt.toString()));
        return new CreatedShare(shareId, token, "/s/" + token, permission, expiresAt);
    }

    @Override
    public List<ShareView> list(String documentId) {
        Document doc = requireOwned(documentId);
        OffsetDateTime now = OffsetDateTime.now();
        return shareRepository.listByDocument(doc.getId()).stream()
                .map(row -> toView(row, now))
                .toList();
    }

    @Override
    @Transactional
    public void revoke(String shareId) {
        if (!Ids.isHex16(shareId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "共享 ID 格式不合法");
        }
        String ownerId = AuthContext.require().userId();
        DocumentShareRow row = shareRepository.findById(shareId);
        if (row == null || !ownerId.equalsIgnoreCase(row.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "共享链接不存在");
        }
        if (!"A".equals(row.getStatus())) {
            return; // 已撤销/已过期：DELETE 幂等
        }
        if (shareRepository.revoke(shareId) == 0) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "链接状态已变化，请刷新后重试");
        }
        auditService.record("SHARE_REVOKED", "DOCUMENT", row.getDocumentId(), row.getDocumentId(), "200",
                Map.of("shareId", shareId));
    }

    @Override
    public PublicShare publicView(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "共享链接不存在");
        }
        DocumentShareRow share = shareRepository.findByToken(token.trim());
        if (share == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "共享链接不存在");
        }
        if (!"A".equals(share.getStatus())) {
            throw new CidasBusinessException(ErrorCode.SHARE_LINK_EXPIRED);
        }
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(OffsetDateTime.now())) {
            shareRepository.markExpired(share.getId());
            throw new CidasBusinessException(ErrorCode.SHARE_LINK_EXPIRED);
        }
        DocumentViewRow doc = documentRepository.detail(share.getDocumentId())
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.SHARE_LINK_EXPIRED));
        if ("DELETED".equals(doc.getStatus())) {
            throw new CidasBusinessException(ErrorCode.SHARE_LINK_EXPIRED);
        }
        String text = doc.getCurrentVersionId() == null ? ""
                : chunkRepository.selectAllByVersion(doc.getCurrentVersionId()).stream()
                        .map(c -> c.getContentText() == null ? "" : c.getContentText())
                        .collect(Collectors.joining());
        return new PublicShare(doc.getTitle(), doc.getVersionNo(), share.getPermission(), text);
    }

    private ShareView toView(DocumentShareRow row, OffsetDateTime now) {
        String status = row.getStatus();
        if ("A".equals(status) && row.getExpiresAt() != null && row.getExpiresAt().isBefore(now)) {
            shareRepository.markExpired(row.getId());
            status = "E";
        }
        return new ShareView(row.getId(), row.getLinkToken(), "/s/" + row.getLinkToken(),
                row.getPermission(), status, row.getExpiresAt(), row.getCreateTime());
    }

    private Document requireOwned(String documentId) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档 ID 格式不合法");
        }
        String ownerId = AuthContext.require().userId();
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        if (!ownerId.equalsIgnoreCase(doc.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}