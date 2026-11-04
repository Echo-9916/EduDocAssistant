package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.ai.infrastructure.AiCitation;
import com.cidas.edudoc.ai.infrastructure.AiResult;
import com.cidas.edudoc.ai.infrastructure.AiTask;

import java.util.List;

/**
 * 问答落库事务端口（C-06）：USER+ASSISTANT 双消息与任务/结果/引用一次事务写入，
 * sequence_no 同事务内取号（uk_qa_message_1 兜并发）。
 */
public interface QaTxService {

    record Write(String sessionId, String question,
                 String assistantText, String assistantStatus,
                 AiTask task, AiResult result, List<AiCitation> citations) {
    }

    record Written(String questionMessageId, String answerMessageId) {
    }

    Written persist(Write write);
}
