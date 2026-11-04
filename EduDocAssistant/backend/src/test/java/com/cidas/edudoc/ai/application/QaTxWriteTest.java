package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.ai.application.impl.QaTxServiceImpl;
import com.cidas.edudoc.ai.infrastructure.AiCitation;
import com.cidas.edudoc.ai.infrastructure.AiResult;
import com.cidas.edudoc.ai.infrastructure.AiResultRepository;
import com.cidas.edudoc.ai.infrastructure.AiTask;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.ai.infrastructure.QaMessage;
import com.cidas.edudoc.ai.infrastructure.QaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REFUSED 双消息与 sequence_no 自增（uk_qa_message_1 口径）。 */
class QaTxWriteTest {

    private final QaRepository qaRepository = mock(QaRepository.class);
    private final AiTaskRepository aiTaskRepository = mock(AiTaskRepository.class);
    private final AiResultRepository aiResultRepository = mock(AiResultRepository.class);
    private final QaTxService service = new QaTxServiceImpl(qaRepository, aiTaskRepository, aiResultRepository);

    @SuppressWarnings("unchecked")
    private List<QaMessage> capturedMessages() {
        ArgumentCaptor<List<QaMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(qaRepository).insertMessages(captor.capture());
        return captor.getValue();
    }

    @Test
    void refusedWritesTwoMessagesWithNextSequencePair() {
        when(qaRepository.nextSequence("S1")).thenReturn(4);

        service.persist(new QaTxService.Write("S1", "问题？", "拒答文案", "REFUSED", null, null, List.of()));

        List<QaMessage> messages = capturedMessages();
        assertEquals(2, messages.size());
        assertEquals("USER", messages.get(0).getRole());
        assertEquals(5, messages.get(0).getSequenceNo());
        assertEquals("READY", messages.get(0).getStatus());
        assertEquals("ASSISTANT", messages.get(1).getRole());
        assertEquals(6, messages.get(1).getSequenceNo());
        assertEquals("REFUSED", messages.get(1).getStatus());
        verify(aiTaskRepository, never()).insertOrReuse(any());
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }

    @Test
    void answerWriteIncludesTaskResultCitationsAndMessageLink() {
        when(qaRepository.nextSequence("S1")).thenReturn(0);
        AiTask task = new AiTask("T1", "D1", "V1", "QA", "SUCCEEDED", 5, null, "{}", "U1", 100,
                null, null, 0);
        AiResult result = new AiResult("R1", "T1", "ANSWER", "答案", "{}", "m", "p", 1, 2);

        service.persist(new QaTxService.Write("S1", "问题？", "答案", "READY",
                task, result, List.of(new AiCitation("C1", "R1", "H1", 1, 0, 9, "原文", 1))));

        List<QaMessage> messages = capturedMessages();
        assertEquals("T1", messages.get(1).getAiTaskId());
        verify(aiTaskRepository).insertOrReuse(task);
        verify(aiResultRepository).insert(result);
        verify(aiResultRepository).insertCitations(anyList());
    }
}
