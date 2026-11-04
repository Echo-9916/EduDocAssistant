package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.ai.application.AiGateway.Keypoint;
import com.cidas.edudoc.ai.application.AiGateway.KeypointOutcome;
import com.cidas.edudoc.ai.application.AiGateway.SourceChunk;
import com.cidas.edudoc.ai.infrastructure.AiCitation;
import com.cidas.edudoc.ai.infrastructure.AiResult;
import com.cidas.edudoc.ai.infrastructure.AiTask;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.ai.infrastructure.AiTaskWorkerProperties;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.VersionChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C-05 要点处理：乱序入参按引用块重排为文档顺序、非法 chunkId 丢弃、条数 <5 标记 underMin。 */
class KeypointHandlerTest {

    private static final String TASK_ID = "AA0000000000000000000000000000C1";
    private static final String VERSION = "AA0000000000000000000000000000B1";

    private final AiTaskRepository aiTaskRepository = mock(AiTaskRepository.class);
    private final ChunkEmbeddingRepository embeddingRepository = mock(ChunkEmbeddingRepository.class);
    private final AiGateway aiGateway = mock(AiGateway.class);
    private final AiResultTxService resultTxService = mock(AiResultTxService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AiTaskWorker worker = new AiTaskWorker(
            mock(org.springframework.data.redis.core.StringRedisTemplate.class), new ObjectMapper(),
            new AiTaskWorkerProperties(false, 1, "s", "g", 60, 6000),
            aiTaskRepository, embeddingRepository, aiGateway, new CitationService(),
            resultTxService, auditService);

    private static VersionChunk chunk(String id, int no, int charStart) {
        return new VersionChunk(id, no, no, charStart, charStart + 9, "文本" + id, "READY");
    }

    private void givenKeypoints(List<VersionChunk> chunks, List<Keypoint> modelOutput) {
        when(aiTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(
                new AiTask(TASK_ID, "D1", VERSION, "KEYPOINT", "QUEUED", 5, null, "{}", "U1", 0, null, null, 0)));
        when(aiTaskRepository.casStart(TASK_ID)).thenReturn(true);
        when(aiTaskRepository.updateProgress(eq(TASK_ID), anyInt())).thenReturn(true);
        when(embeddingRepository.selectVersionChunks(VERSION)).thenReturn(chunks);
        when(aiGateway.extractKeypoints(anyList())).thenAnswer(inv -> {
            List<SourceChunk> group = inv.getArgument(0);
            assertTrue(!group.isEmpty(), "分组入参非空");
            return new KeypointOutcome("m", "kp-v1", null, modelOutput);
        });
        when(resultTxService.completeTask(any(), anyList())).thenReturn(true);
    }

    @Test
    void reordersOutOfOrderKeypointsByDocPositionAndDropsIllegalRefs() {
        List<VersionChunk> chunks = List.of(chunk("C1", 1, 0), chunk("C2", 2, 10), chunk("C3", 3, 20));
        // 模型乱序输出 + 一个不存在的 chunkId + 一个空 chunkId
        List<Keypoint> modelOutput = List.of(
                new Keypoint("要点三", "C3"),
                new Keypoint("幽灵要点", "NO_SUCH_CHUNK"),
                new Keypoint("要点一", "C1"),
                new Keypoint("无引用要点", null),
                new Keypoint("要点二", "C2"));
        givenKeypoints(chunks, modelOutput);

        worker.processTask(TASK_ID);

        ArgumentCaptor<AiResult> result = ArgumentCaptor.forClass(AiResult.class);
        ArgumentCaptor<List<AiCitation>> citations = ArgumentCaptor.forClass(List.class);
        verify(resultTxService).completeTask(result.capture(), citations.capture());
        assertEquals("KEYPOINT", result.getValue().getResultType());
        // 文档顺序：要点一/二/三，非法与空引用丢弃
        String content = result.getValue().getContentText();
        assertTrue(content.indexOf("要点一") < content.indexOf("要点二"));
        assertTrue(content.indexOf("要点二") < content.indexOf("要点三"));
        assertTrue(!content.contains("幽灵"), "非法引用应丢弃");
        assertTrue(!content.contains("无引用"), "空 chunkId 应丢弃");
        assertEquals(3, citations.getValue().size());
        assertEquals(List.of(1, 2, 3), citations.getValue().stream().map(AiCitation::getRankNo).toList());
        assertEquals(List.of("C1", "C2", "C3"),
                citations.getValue().stream().map(AiCitation::getChunkId).toList());
        // <5 条 → underMin 标记（不判失败）
        assertTrue(result.getValue().getStructuredJson().contains("\"underMin\":true"),
                result.getValue().getStructuredJson());
    }

    @Test
    void fiveKeypointsNoUnderMinFlag() {
        List<VersionChunk> chunks = List.of(chunk("C1", 1, 0), chunk("C2", 2, 10), chunk("C3", 3, 20),
                chunk("C4", 4, 30), chunk("C5", 5, 40));
        givenKeypoints(chunks, List.of(
                new Keypoint("五", "C5"), new Keypoint("四", "C4"), new Keypoint("三", "C3"),
                new Keypoint("二", "C2"), new Keypoint("一", "C1")));

        worker.processTask(TASK_ID);

        ArgumentCaptor<AiResult> result = ArgumentCaptor.forClass(AiResult.class);
        verify(resultTxService).completeTask(result.capture(), anyList());
        assertTrue(result.getValue().getStructuredJson().contains("\"underMin\":false"));
        assertTrue(result.getValue().getStructuredJson().contains("kp-v1")
                || "kp-v1".equals(result.getValue().getPromptVersion()));
    }

}
