package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.ai.application.AiGateway.EmbedOutcome;
import com.cidas.edudoc.ai.infrastructure.AiModelProfile;
import com.cidas.edudoc.ai.infrastructure.AiModelProfileRepository;
import com.cidas.edudoc.ai.infrastructure.AiTask;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.ChunkVector;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.PendingChunk;
import com.cidas.edudoc.ai.infrastructure.EmbedProperties;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C-03 向量化 worker：批量切分、维度校验整批不落、重投幂等、过期事件跳过、失败映射。 */
class EmbedWorkerTest {

    private static final String VERSION = "V1";
    private static final String DOC = "D1";

    private final StringRedisTemplateHolder redisHolder = new StringRedisTemplateHolder();
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final AiTaskRepository aiTaskRepository = mock(AiTaskRepository.class);
    private final AiModelProfileRepository profileRepository = mock(AiModelProfileRepository.class);
    private final ChunkEmbeddingRepository embeddingRepository = mock(ChunkEmbeddingRepository.class);
    private final AiGateway aiGateway = mock(AiGateway.class);
    private final EmbeddingTxService embeddingTxService = mock(EmbeddingTxService.class);
    private final EmbedWorker worker = new EmbedWorker(
            redisHolder.template, new ObjectMapper(),
            new EmbedProperties(false, 2, 25, "edudoc:stream:document-embed", "cg-document-embed"),
            documentRepository, aiTaskRepository, profileRepository, embeddingRepository,
            aiGateway, embeddingTxService);

    /** 极简 StringRedisTemplate mock（worker 构造需要，流程测试不触达）。 */
    private static final class StringRedisTemplateHolder {
        final org.springframework.data.redis.core.StringRedisTemplate template =
                mock(org.springframework.data.redis.core.StringRedisTemplate.class);
    }

    private static Document readyDoc() {
        Document doc = new Document(DOC, "C1", "U1", "t", "PDF", "READY", VERSION);
        return doc;
    }

    private static AiTask queuedTask() {
        return new AiTask("T1", DOC, VERSION, "EMBED", "QUEUED", 5, null, "{}", "U1", 0, "E1", null, 0);
    }

    private static AiModelProfile profile() {
        AiModelProfile p = new AiModelProfile("P1", "EMB_BGE_M3", "EMBEDDING", "BAAI/bge-m3", 1024, "COSINE", "A");
        return p;
    }

    private void givenReadyDocWithProfile() {
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(readyDoc()));
        when(profileRepository.findByCode("EMB_BGE_M3")).thenReturn(Optional.of(profile()));
        when(aiTaskRepository.insertOrReuse(any())).thenReturn(queuedTask());
        when(aiTaskRepository.casStart("T1")).thenReturn(true);
    }

    private static List<PendingChunk> chunks(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new PendingChunk("CHUNK" + i, i, "文本" + i))
                .toList();
    }

    /** embed 返回与入参等长的向量批（默认 1024 维）。 */
    private void givenEmbedOk(int dim) {
        when(aiGateway.embed(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return new EmbedOutcome("bge-m3", java.util.stream.IntStream.range(0, texts.size())
                    .mapToObj(i -> new float[dim]).toList());
        });
    }

    @Test
    void embedsInBatchesOf25AndPersistsPerBatch() {
        givenReadyDocWithProfile();
        when(embeddingRepository.selectPendingChunks(VERSION)).thenReturn(chunks(60));
        givenEmbedOk(1024);

        worker.processEvent("E1", DOC, VERSION);

        verify(aiGateway, times(3)).embed(anyList());
        verify(embeddingTxService, times(3)).storeBatch(eq("T1"), eq("P1"), eq(1024), anyList(), anyInt());
        verify(aiTaskRepository).markSucceeded("T1");
    }

    @Test
    void batchPartitionsAre252510() {
        givenReadyDocWithProfile();
        when(embeddingRepository.selectPendingChunks(VERSION)).thenReturn(chunks(60));
        givenEmbedOk(1024);

        worker.processEvent("E1", DOC, VERSION);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiGateway, times(3)).embed(captor.capture());
        assertEquals(List.of(25, 25, 10), captor.getAllValues().stream().map(List::size).toList());
        ArgumentCaptor<List<ChunkVector>> vectors = ArgumentCaptor.forClass(List.class);
        verify(embeddingTxService, times(3)).storeBatch(eq("T1"), eq("P1"), eq(1024), vectors.capture(), anyInt());
        assertEquals(List.of(25, 25, 10), vectors.getAllValues().stream().map(List::size).toList());
        // 向量字面量形态 TO_VECTOR 可解析
        assertTrue(vectors.getAllValues().get(0).get(0).embeddingText().startsWith("["));
    }

    @Test
    void laterBatchDimensionFailureKeepsEarlierBatchesUnpersisted() {
        // 1105 审查 P1-1：两阶段化——全部批次 embed+校验成功后才允许落库（整版无半向量）
        givenReadyDocWithProfile();
        when(embeddingRepository.selectPendingChunks(VERSION)).thenReturn(chunks(30)); // 25 + 5 两批
        when(aiGateway.embed(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            int dim = texts.size() == 25 ? 1024 : 768; // 第二批维度错误
            return new EmbedOutcome("m", java.util.stream.IntStream.range(0, texts.size())
                    .mapToObj(i -> new float[dim]).toList());
        });

        worker.processEvent("E1", DOC, VERSION);

        verify(embeddingTxService, never()).storeBatch(any(), any(), anyInt(), anyList(), anyInt());
        verify(aiTaskRepository).markFailed("T1", "50201");
        verify(aiTaskRepository, never()).markSucceeded(any());
    }

    @Test
    void dimensionMismatchFailsTaskAndPersistsNothing() {
        givenReadyDocWithProfile();
        when(embeddingRepository.selectPendingChunks(VERSION)).thenReturn(chunks(3));
        when(aiGateway.embed(anyList())).thenReturn(new EmbedOutcome("m",
                List.of(new float[768], new float[768], new float[768])));

        worker.processEvent("E1", DOC, VERSION);

        verify(embeddingTxService, never()).storeBatch(any(), any(), anyInt(), anyList(), anyInt());
        verify(aiTaskRepository).markFailed("T1", "50201");
        verify(aiTaskRepository, never()).markSucceeded(any());
    }

    @Test
    void redeliveredEventWithActiveTaskSkips() {
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(readyDoc()));
        AiTask running = queuedTask();
        running.setStatus("RUNNING");
        when(aiTaskRepository.insertOrReuse(any())).thenReturn(running);

        worker.processEvent("E1", DOC, VERSION);

        verify(aiTaskRepository, never()).casStart(any());
        verify(aiGateway, never()).embed(anyList());
    }

    @Test
    void staleVersionEventIsSkippedWithoutTask() {
        Document doc = readyDoc();
        doc.setCurrentVersionId("OTHER_VERSION");
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(doc));

        worker.processEvent("E1", DOC, VERSION);

        verify(aiTaskRepository, never()).insertOrReuse(any());
    }

    @Test
    void gatewayFailureMarksTaskFailedWithErrorCode() {
        givenReadyDocWithProfile();
        when(embeddingRepository.selectPendingChunks(VERSION)).thenReturn(chunks(3));
        when(aiGateway.embed(anyList())).thenThrow(new CidasBusinessException(
                ErrorCode.MODEL_FAILED, "ai-service 不可达", true, java.util.Map.of()));

        worker.processEvent("E1", DOC, VERSION);

        verify(aiTaskRepository).markFailed("T1", "50201");
    }

    @Test
    void emptyPendingSucceedsImmediately() {
        givenReadyDocWithProfile();
        when(embeddingRepository.selectPendingChunks(VERSION)).thenReturn(List.of());

        worker.processEvent("E1", DOC, VERSION);

        verify(aiGateway, never()).embed(anyList());
        verify(aiTaskRepository).markSucceeded("T1");
    }

    @Test
    void missingProfileFailsTaskWith50301() {
        when(documentRepository.findById(DOC)).thenReturn(Optional.of(readyDoc()));
        when(aiTaskRepository.insertOrReuse(any())).thenReturn(queuedTask());
        when(aiTaskRepository.casStart("T1")).thenReturn(true);
        when(profileRepository.findByCode("EMB_BGE_M3")).thenReturn(Optional.empty());

        worker.processEvent("E1", DOC, VERSION);

        verify(aiTaskRepository).markFailed("T1", "50301");
    }

    @Test
    void vectorLiteralRoundTrips() {
        assertEquals("[0.0,1.5,-2.25]", EmbedWorker.toVectorLiteral(new float[]{0f, 1.5f, -2.25f}));
    }
}
