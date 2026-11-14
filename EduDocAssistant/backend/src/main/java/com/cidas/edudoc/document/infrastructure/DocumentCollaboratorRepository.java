package com.cidas.edudoc.document.infrastructure;

import com.cidas.edudoc.common.persistence.Ids;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 协作者读写（SQL 委托给 {@link DocumentCollaboratorMapper}）。 */
@Repository
@RequiredArgsConstructor
public class DocumentCollaboratorRepository {

    private final DocumentCollaboratorMapper mapper;

    public String findSessionId(String documentIdHex) {
        return mapper.selectSessionIdByDocument(documentIdHex);
    }

    /** 每文档一个协作会话；并发首建撞 provider_doc_key 时回读已有会话。 */
    public String ensureSession(String documentIdHex, String baseVersionIdHex, String createdByHex) {
        String existing = mapper.selectSessionIdByDocument(documentIdHex);
        if (existing != null) {
            return existing;
        }
        try {
            String idHex = Ids.hex16();
            mapper.insertSession(idHex, documentIdHex, baseVersionIdHex, "doc:" + documentIdHex, createdByHex);
            return idHex;
        } catch (DuplicateKeyException e) {
            return mapper.selectSessionIdByDocument(documentIdHex);
        }
    }

    public List<CollaboratorRow> listMembers(String sessionIdHex) {
        return mapper.selectMembers(sessionIdHex);
    }

    public CollaboratorRow findMember(String sessionIdHex, String userIdHex) {
        return mapper.selectMember(sessionIdHex, userIdHex);
    }

    public boolean isActiveMember(String documentIdHex, String userIdHex) {
        return mapper.countActiveMember(documentIdHex, userIdHex) > 0;
    }

    public void addMember(String sessionIdHex, String userIdHex, String permissionLevel) {
        CollaboratorRow existing = mapper.selectMember(sessionIdHex, userIdHex);
        if (existing != null && !"REMOVED".equals(existing.getConnectionStatus())) {
            throw new com.cidas.edudoc.common.api.CidasBusinessException(
                    com.cidas.edudoc.common.api.ErrorCode.CONFLICT, "该账号已是协作者");
        }
        if (existing != null) {
            mapper.reactivateMember(sessionIdHex, userIdHex, permissionLevel);
            return;
        }
        mapper.insertMember(Ids.hex16(), sessionIdHex, userIdHex, permissionLevel);
    }

    public int removeMember(String sessionIdHex, String userIdHex) {
        return mapper.removeMember(sessionIdHex, userIdHex);
    }

    public CollaboratorRow findUserByUsername(String username) {
        return mapper.selectUserByUsername(username);
    }
}