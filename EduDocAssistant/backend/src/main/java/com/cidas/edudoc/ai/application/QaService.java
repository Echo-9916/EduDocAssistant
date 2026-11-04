package com.cidas.edudoc.ai.application;

import java.util.List;

/**
 * 问答服务（C-06，§3.2.2 同步链路）：embed→topK 检索→rerank（缺省降级）→证据阈值→
 * evidence-only 生成→引用硬校验→落库。拒答与超时都是业务结局，不伪造答案。
 */
public interface QaService {

    record SessionView(String sessionId, String documentId, String title, String status) {
    }

    record CitationView(String chunkId, Integer pageNo, Integer charStart, Integer charEnd, String quoteText) {
    }

    record MessageView(String messageId, String role, String contentText, String status,
                       int sequenceNo, List<CitationView> citations) {
    }

    /** READY：答案+引用；REFUSED：拒答（业务成功）。FAILED 走异常（504/502），但消息已落库。 */
    record AskResult(String sessionId, String questionMessageId, String answerMessageId,
                     String status, String answer, List<CitationView> citations) {
    }

    SessionView createSession(String documentId, String title);

    List<SessionView> listSessions(String documentId);

    AskResult ask(String sessionId, String question);

    List<MessageView> messages(String sessionId);
}
