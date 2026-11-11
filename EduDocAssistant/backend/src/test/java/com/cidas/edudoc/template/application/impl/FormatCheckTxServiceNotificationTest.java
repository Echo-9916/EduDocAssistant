package com.cidas.edudoc.template.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.template.infrastructure.FormatCheckRepository;
import com.cidas.edudoc.template.infrastructure.FormatIssueRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 2-5 生产者③（格式校验结论）：blocking&gt;0 时在结论回写同事务内给文档 owner 发 FORMAT 事件；
 * PASSED、迟到/重复投递（RUNNING 守卫 0 行）与 owner 缺失都不发。
 */
class FormatCheckTxServiceNotificationTest {

    private static final String CHECK_ID = "C".repeat(32);
    private static final String DOCUMENT_ID = "D".repeat(32);
    private static final String VERSION_ID = "E".repeat(32);
    private static final String OWNER_ID = "F".repeat(32);

    private final FormatCheckRepository checkRepository = mock(FormatCheckRepository.class);
    private final FormatIssueRepository issueRepository = mock(FormatIssueRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final FormatCheckTxServiceImpl service = new FormatCheckTxServiceImpl(
            checkRepository, issueRepository, auditService, outboxEventRepository);

    @Test
    @SuppressWarnings("unchecked")
    void blockingResultNotifiesDocumentOwnerInSameWrite() {
        when(checkRepository.updateResult(eq(CHECK_ID), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(1);
        when(checkRepository.selectDocumentOwner(DOCUMENT_ID)).thenReturn(OWNER_ID);

        service.writeResult(CHECK_ID, List.of(), 3, 2, "FAILED", DOCUMENT_ID, VERSION_ID);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventRepository).publish(eq("FORMAT_CHECK"), eq(CHECK_ID),
                eq("NOTIFICATION_REQUESTED"), captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertEquals("FORMAT", payload.get("notifType"));
        assertEquals(CHECK_ID, payload.get("aggregateRef"));
        assertNull(payload.get("dedupDate"));
        assertEquals(List.of(OWNER_ID), payload.get("recipientIds"));
        assertEquals("DOCUMENT", payload.get("resourceType"));
        assertEquals(DOCUMENT_ID, payload.get("resourceRef"));
        assertNull(payload.get("actorId")); // 系统触发
        assertTrue(((String) payload.get("body")).contains("2 项阻塞"));
        // 业务写、审计与 publish 同一事务：审计照常落
        verify(auditService).record(eq("FORMAT_CHECK_DONE"), eq("FORMAT_CHECK"), eq(CHECK_ID),
                eq(DOCUMENT_ID), eq("42202"), any());
    }

    @Test
    void passingResultDoesNotNotify() {
        when(checkRepository.updateResult(eq(CHECK_ID), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(1);

        service.writeResult(CHECK_ID, List.of(), 1, 0, "PASSED", DOCUMENT_ID, VERSION_ID);

        verify(outboxEventRepository, never()).publish(any(), any(), any(), any());
        verify(checkRepository, never()).selectDocumentOwner(anyString());
    }

    @Test
    void staleOrDuplicatedConclusionDoesNotNotify() {
        // RUNNING 守卫 0 行 = 迟到/重复投递：结论不落库，事件也不能再发
        when(checkRepository.updateResult(eq(CHECK_ID), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(0);

        service.writeResult(CHECK_ID, List.of(), 3, 2, "FAILED", DOCUMENT_ID, VERSION_ID);

        verify(outboxEventRepository, never()).publish(any(), any(), any(), any());
    }

    @Test
    void missingOwnerSkipsPublishButKeepsConclusion() {
        when(checkRepository.updateResult(eq(CHECK_ID), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(1);
        when(checkRepository.selectDocumentOwner(DOCUMENT_ID)).thenReturn(null);

        service.writeResult(CHECK_ID, List.of(), 3, 2, "FAILED", DOCUMENT_ID, VERSION_ID);

        verify(outboxEventRepository, never()).publish(any(), any(), any(), any());
        verify(issueRepository).insertDrafts(eq(CHECK_ID), any());
    }
}
