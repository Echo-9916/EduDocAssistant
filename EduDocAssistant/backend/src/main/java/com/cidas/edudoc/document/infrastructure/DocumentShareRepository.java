package com.cidas.edudoc.document.infrastructure;

import com.cidas.edudoc.common.persistence.Ids;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/** 共享链接读写（SQL 委托给 {@link DocumentShareMapper}）。 */
@Repository
@RequiredArgsConstructor
public class DocumentShareRepository {

    private final DocumentShareMapper mapper;

    public String create(String documentIdHex, String ownerIdHex, String token, OffsetDateTime expiresAt,
                         String permissionLevel) {
        String shareId = Ids.hex16();
        mapper.insertShare(shareId, documentIdHex, ownerIdHex, token, expiresAt);
        mapper.insertLinkGrant(Ids.hex16(), shareId, permissionLevel, ownerIdHex);
        return shareId;
    }

    public List<DocumentShareRow> listByDocument(String documentIdHex) {
        return mapper.selectByDocument(documentIdHex);
    }

    public DocumentShareRow findByToken(String token) {
        return mapper.selectByToken(token);
    }

    public DocumentShareRow findById(String shareIdHex) {
        return mapper.selectById(shareIdHex);
    }

    public int revoke(String shareIdHex) {
        return mapper.revoke(shareIdHex);
    }

    public void markExpired(String shareIdHex) {
        mapper.markExpired(shareIdHex);
    }
}