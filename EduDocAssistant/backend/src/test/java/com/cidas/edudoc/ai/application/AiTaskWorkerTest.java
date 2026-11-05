package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.ai.application.AiGateway.SourceChunk;
import com.cidas.edudoc.ai.application.AiGateway.SummarizeOutcome;
import com.cidas.edudoc.ai.application.AiGateway.SummarySegment;
import com.cidas.edudoc.ai.application.AiGateway.TokenUsage;
import com.cidas.edudoc.ai.infrastructure.AiCitation;
import com.cidas.edudoc.ai.infrastructure.AiResult;
import com.cidas.edudoc.ai.infrastructure.AiTask;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.ai.infrastructure.AiTaskWorkerProperties;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.VersionChunk;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C-04 worker：状态机 CAS、取消竞争、分组进度、非法引用丢弃、审计不含正文。 */
class AiTaskWorkerTest {

    private static final String TASK_ID = "AA0000000000000000000000000000C1";
    private static final String VERSION = "AA0000000000000000000000000000B1";

    private final org.springframework.data.redis.core.StringRedisTemplate redis =
            mock(org.springframework.data.redis.core.StringRedisTemplate.class);
    private final AiTaskRepository aiTaskRepository = mock(AiTaskRepository.class);
    private final ChunkEmbeddingRepository embeddingRepository = mock(ChunkEmbeddingRepository.class);
    private final AiGateway aiGateway = mock(AiGateway.class);
    private final AiResultTxService resultTxService = mock(AiResultTxService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AiTaskWorker worker = new AiTaskWorker(redis, new ObjectMapper(),
            new AiTaskWorkerProperties(false, 1, "edudoc:stream:ai-task", "cg-ai-task", 60, 50),
            aiTaskRepository, embeddingRepository, aiGateway, new CitationService(),
            resultTxService, auditService);

    private static AiTask task(String status) {
        return new AiTask(TASK_ID, "D1", VERSION, "SUMMARY", status, 5, null, "{}", "U1", 0, null, null, 0);
    }

    /** 每块 21 字符：groupChars=50 时三块切成 [2,1]。 */
    private static VersionChunk chunk(String id, int no, int page, int start) {
        return new VersionChunk(id, no, page, start, start + 20, "字".repeat(19) + id, "READY");
    }

    private void givenQueuedRunningChunks(VersionChunk... chunks) {
        when(aiTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task("QUEUED")));
        when(aiTaskRepository.casStart(TASK_ID)).thenReturn(true);
        when(embeddingRepository.selectVersionChunks(VERSION)).thenReturn(List.of(chunks));
        when(aiTaskRepository.updateProgress(eq(TASK_ID), anyInt())).thenReturn(true);
    }

    @Test
    void duplicateDeliverySkipsCas() {
        when(aiTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task("RUNNING")));

        worker.processTask(TASK_ID);

        verify(aiTaskRepository, never()).casStart(anyString());
        verify(aiGateway, never()).summarize(anyList());
    }

    @Test
    void lostCasSkipsExecution() {
        when(aiTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task("QUEUED")));
        when(aiTaskRepository.casStart(TASK_ID)).thenReturn(false);

        worker.processTask(TASK_ID);

        verify(aiGateway, never()).summarize(anyList());
    }

    @Test
    void summaryGroupsProgressAndDropsIllegalCitations() {
        // groupChars=50：三条 20 字符块 → 2+1 两组
        givenQueuedRunningChunks(chunk("A1", 1, 1, 0), chunk("A2", 2, 1, 21), chunk("A3", 3, 2, 42));
        when(aiGateway.summarize(anyList())).thenAnswer(inv -> {
            List<SourceChunk> group = inv.getArgument(0);
            String firstId = group.get(0).chunkId();
            return new SummarizeOutcome("Kimi-K2", "sum-v1", new TokenUsage(10, 5),
                    List.of(new SummarySegment("摘要-" + firstId, List.of(firstId, "NOT_A_CHUNK"))));
        });
        when(resultTxService.completeTask(any(), anyList())).thenReturn(true);

        worker.processTask(TASK_ID);

        verify(aiGateway, times(2)).summarize(anyList());
        verify(aiTaskRepository, times(2)).updateProgress(eq(TASK_ID), anyInt());
        ArgumentCaptor<AiResult> result = ArgumentCaptor.forClass(AiResult.class);
        ArgumentCaptor<List<AiCitation>> citations = ArgumentCaptor.forClass(List.class);
        verify(resultTxService).completeTask(result.capture(), citations.capture());
        assertEquals("SUMMARY", result.getValue().getResultType());
        assertTrue(result.getValue().getContentText().contains("摘要-A1"));
        assertEquals("sum-v1", result.getValue().getPromptVersion());
        // 非法 chunkId 丢弃、合法引用回填 page/char、rank 连续
        // 审查发现 5：structured_json 组内 chunkIds 同样只留合法引用（非法引用丢弃要一次做全）
        assertTrue(!result.getValue().getStructuredJson().contains("NOT_A_CHUNK"),
                "结构化结果不得保留非法 chunkId：" + result.getValue().getStructuredJson());
        assertTrue(result.getValue().getStructuredJson().contains("A1"));
        assertEquals(2, citations.getValue().size());
        assertEquals(1, citations.getValue().get(0).getRankNo());
        assertEquals(2, citations.getValue().get(1).getRankNo());
        assertEquals(2, citations.getValue().get(1).getPageNo());
        assertEquals(42, citations.getValue().get(1).getCharStart());
        assertEquals(result.getValue().getId(), citations.getValue().get(0).getResultId());
        // 审计不写正文
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq("AI_TASK_SUCCEEDED"), eq("AI_TASK"), eq(TASK_ID), eq("D1"),
                eq("200"), details.capture());
        assertTrue(!String.valueOf(details.getValue()).contains("摘要-A1"),
                "审计细节不得含正文：" + details.getValue());
    }

    @Test
    void cancelDetectedViaProgressGuardAbortsSilently() {
        givenQueuedRunningChunks(chunk("A1", 1, 1, 0), chunk("A2", 2, 1, 21), chunk("A3", 3, 2, 42));
        when(aiGateway.summarize(anyList())).thenReturn(new SummarizeOutcome("m", "p", null,
                List.of(new SummarySegment("摘要", List.of("A1")))));
        when(aiTaskRepository.updateProgress(eq(TASK_ID), anyInt())).thenReturn(false); // 已被取消

        worker.processTask(TASK_ID);

        verify(resultTxService, never()).completeTask(any(), anyList());
        verify(auditService, never()).record(eq("AI_TASK_SUCCEEDED"), any(), any(), any(), any(), any());
    }

    @Test
    void writebackGuardLossDiscardsResultWithoutAudit() {
        givenQueuedRunningChunks(chunk("A1", 1, 1, 0));
        when(aiGateway.summarize(anyList())).thenReturn(new SummarizeOutcome("m", "p", null,
                List.of(new SummarySegment("摘要", List.of("A1")))));
        when(resultTxService.completeTask(any(), anyList())).thenReturn(false);

        worker.processTask(TASK_ID);

        verify(auditService, never()).record(eq("AI_TASK_SUCCEEDED"), any(), any(), any(), any(), any());
    }

    @Test
    void gatewayFailureMarksFailedAndAudits() {
        givenQueuedRunningChunks(chunk("A1", 1, 1, 0));
        when(aiGateway.summarize(anyList())).thenThrow(new CidasBusinessException(
                ErrorCode.MODEL_FAILED, "模型服务失败", true, Map.of()));

        worker.processTask(TASK_ID);

        verify(aiTaskRepository).markFailed(TASK_ID, "50201");
        verify(auditService).record(eq("AI_TASK_FAILED"), eq("AI_TASK"), eq(TASK_ID), eq("D1"),
                eq("50201"), any());
    }

    @Test
    void chunkGroupingRespectsCharLimit() {
        List<VersionChunk> chunks = List.of(
                chunk("A1", 1, 1, 0), chunk("A2", 2, 1, 21), chunk("A3", 3, 2, 42));
        List<List<VersionChunk>> groups = AiTaskWorker.groupChunks(chunks, 50);
        assertEquals(List.of(2, 1), groups.stream().map(List::size).toList());
        assertEquals(1, AiTaskWorker.groupChunks(chunks, 6000).size());
    }
}
