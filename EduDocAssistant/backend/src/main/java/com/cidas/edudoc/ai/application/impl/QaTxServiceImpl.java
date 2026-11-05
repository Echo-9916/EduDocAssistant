package com.cidas.edudoc.ai.application.impl;

import com.cidas.edudoc.ai.application.QaTxService;
import com.cidas.edudoc.ai.infrastructure.AiResultRepository;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.ai.infrastructure.QaMessage;
import com.cidas.edudoc.ai.infrastructure.QaRepository;
import com.cidas.edudoc.common.persistence.Ids;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QaTxServiceImpl implements QaTxService {

    private final QaRepository qaRepository;
    private final AiTaskRepository aiTaskRepository;
    private final AiResultRepository aiResultRepository;

    @Override
    @Transactional
    public Written persist(Write write) {
        int base = qaRepository.nextSequence(write.sessionId());
        String questionId = Ids.hex16();
        String answerId = Ids.hex16();
        String taskId = write.task() == null ? null : write.task().getId();
        // 顺序契约（真机冒烟 ORA-02291）：fk_qa_message_2 → edudoc_ai_task，任务行必须先于消息插入
        if (write.task() != null) {
            aiTaskRepository.insertOrReuse(write.task());
            aiResultRepository.insert(write.result());
            aiResultRepository.insertCitations(write.citations());
        }
        qaRepository.insertMessages(List.of(
                new QaMessage(questionId, write.sessionId(), "USER", write.question(), null, base + 1, "READY"),
                new QaMessage(answerId, write.sessionId(), "ASSISTANT", write.assistantText(),
                        taskId, base + 2, write.assistantStatus())));
        return new Written(questionId, answerId);
    }
}
