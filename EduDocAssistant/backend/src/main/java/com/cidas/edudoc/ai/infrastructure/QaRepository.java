package com.cidas.edudoc.ai.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 问答会话与消息读写（SQL 委托 {@link QaMapper}）。 */
@Repository
@RequiredArgsConstructor
public class QaRepository {

    private final QaMapper mapper;

    public void insertSession(QaSession session) {
        mapper.insertSession(session);
    }

    public Optional<QaSession> findSession(String idHex) {
        return Optional.ofNullable(mapper.selectSessionById(idHex));
    }

    public Optional<QaSession> findActiveSession(String documentIdHex, String userIdHex) {
        return Optional.ofNullable(mapper.selectActiveSession(documentIdHex, userIdHex));
    }

    public List<QaSession> findSessions(String documentIdHex, String userIdHex) {
        return mapper.selectSessionsByDocument(documentIdHex, userIdHex);
    }

    public void insertMessages(List<QaMessage> messages) {
        if (!messages.isEmpty()) {
            mapper.insertMessages(messages);
        }
    }

    public int nextSequence(String sessionIdHex) {
        Integer max = mapper.selectMaxSequence(sessionIdHex);
        return max == null ? 0 : max;
    }

    public List<QaMessage> findMessages(String sessionIdHex) {
        return mapper.selectMessages(sessionIdHex);
    }
}
