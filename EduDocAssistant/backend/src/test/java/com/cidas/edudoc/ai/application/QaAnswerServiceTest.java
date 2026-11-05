package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.ai.application.AiGateway.AnswerOutcome;
import com.cidas.edudoc.ai.application.AiGateway.CitationRef;
import com.cidas.edudoc.ai.application.AiGateway.EmbedOutcome;
import com.cidas.edudoc.ai.application.AiGateway.HistoryMessage;
import com.cidas.edudoc.ai.application.AiGateway.RerankHit;
import com.cidas.edudoc.ai.application.AiGateway.SourceChunk;
import com.cidas.edudoc.ai.application.impl.QaServiceImpl;
import com.cidas.edudoc.ai.infrastructure.AiModelProfile;
import com.cidas.edudoc.ai.infrastructure.AiModelProfileRepository;
import com.cidas.edudoc.ai.infrastructure.AiResultRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.SearchHit;
import com.cidas.edudoc.ai.infrastructure.QaMessage;
import com.cidas.edudoc.ai.infrastructure.QaProperties;
import com.cidas.edudoc.ai.infrastructure.QaRepository;
import com.cidas.edudoc.ai.infrastructure.QaSession;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C-06 问答：UT-09 阈值拒答不调生成、UT-10 非法引用拒答、rerank 降级、历史拼接、REFUSED 双消息与 sequence。 */
class QaAnswerServiceTest {

    private static final String SESSION_ID = "AA0000000000000000000000000000E1";
    private static final String DOC_ID = "AA0000000000000000000000000000A1";
    private static final String VERSION = "AA0000000000000000000000000000B1";
    private static final String PROFILE_ID = "AA0000000000000000000000000000F1";

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final ChunkEmbeddingRepository embeddingRepository = mock(ChunkEmbeddingRepository.class);
    private final AiModelProfileRepository profileRepository = mock(AiModelProfileRepository.class);
    private final AiGateway aiGateway = mock(AiGateway.class);
    private final QaRepository qaRepository = mock(QaRepository.class);
    private final QaTxService qaTxService = mock(QaTxService.class);
    private final AiResultRepository aiResultRepository = mock(AiResultRepository.class);
    private final QaService service = new QaServiceImpl(documentRepository, embeddingRepository,
            profileRepository, aiGateway, qaRepository, qaTxService,
            new QaProperties(0.35), aiResultRepository, new ObjectMapper());

    @BeforeEach
    void setUp() {
        AuthContext.set(new UserPrincipal("U1", "stu", null, Set.of("STUDENT"),
                Set.of("AI_QA", "DOCUMENT_READ"), Set.of()));
        when(qaRepository.findSession(SESSION_ID)).thenReturn(Optional.of(
                new QaSession(SESSION_ID, DOC_ID, "U1", "t", "A")));
        Document doc = new Document(DOC_ID, null, "U1", "标题", "PDF", "READY", VERSION);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(profileRepository.findByCode("EMB_BGE_M3")).thenReturn(Optional.of(
                new AiModelProfile(PROFILE_ID, "EMB_BGE_M3", "EMBEDDING", "bge-m3", 1024, "COSINE", "A")));
        when(aiGateway.embed(anyList())).thenReturn(new EmbedOutcome("bge-m3",
                List.of(new float[1024])));
        when(qaTxService.persist(any())).thenReturn(new QaTxService.Written("QM1", "AM1"));
        when(qaRepository.findMessages(SESSION_ID)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private static SearchHit hit(String id, double distance, String text) {
        return new SearchHit(id, text, 1, 0, 9, distance);
    }

    private void givenHits(SearchHit... hits) {
        when(embeddingRepository.searchByVersion(eq(VERSION), eq(PROFILE_ID), anyString(), eq(1024), eq(6)))
                .thenReturn(List.of(hits));
    }

    @Test
    void lowEvidenceScoreRefusesWithoutGeneration() {
        // UT-09：1 - min(0.9)=0.1 < 0.35 → REFUSED，不调生成
        givenHits(hit("H1", 0.9, "无关内容"), hit("H2", 0.95, "无关内容2"));
        when(profileRepository.findByCode("RERANK_BGE_V2")).thenReturn(Optional.empty());

        QaService.AskResult result = service.ask(SESSION_ID, "文档外问题？");

        assertEquals("REFUSED", result.status());
        verify(aiGateway, never()).answer(anyString(), anyList(), anyList());
        ArgumentCaptor<QaTxService.Write> write = ArgumentCaptor.forClass(QaTxService.Write.class);
        verify(qaTxService).persist(write.capture());
        assertEquals("REFUSED", write.getValue().assistantStatus());
    }

    @Test
    void refuseWriteCarriesUserQuestionAndNoTask() {
        givenHits(hit("H1", 0.9, "无关"));
        when(profileRepository.findByCode("RERANK_BGE_V2")).thenReturn(Optional.empty());

        service.ask(SESSION_ID, "问题？");

        ArgumentCaptor<QaTxService.Write> write = ArgumentCaptor.forClass(QaTxService.Write.class);
        verify(qaTxService).persist(write.capture());
        assertEquals("问题？", write.getValue().question());
        assertTrue(write.getValue().task() == null);
    }

    @Test
    void noHitsRefuses() {
        givenHits();
        when(profileRepository.findByCode("RERANK_BGE_V2")).thenReturn(Optional.empty());

        assertEquals("REFUSED", service.ask(SESSION_ID, "问题？").status());
    }

    @Test
    void readyAnswerPersistsTaskResultAndCitations() {
        givenHits(hit("H1", 0.05, "依据原文一"), hit("H2", 0.2, "依据原文二"));
        when(profileRepository.findByCode("RERANK_BGE_V2")).thenReturn(Optional.empty());
        when(aiGateway.answer(anyString(), anyList(), anyList())).thenReturn(new AnswerOutcome(
                "Kimi", "qa-v1", new AiGateway.TokenUsage(50, 20), "答案文本",
                List.of(new CitationRef("H1", null)), false));

        QaService.AskResult result = service.ask(SESSION_ID, "有依据的问题？");

        assertEquals("READY", result.status());
        assertEquals("答案文本", result.answer());
        assertEquals(1, result.citations().size());
        assertEquals("H1", result.citations().get(0).chunkId());
        ArgumentCaptor<QaTxService.Write> write = ArgumentCaptor.forClass(QaTxService.Write.class);
        verify(qaTxService).persist(write.capture());
        assertEquals("QA", write.getValue().task().getTaskType());
        assertEquals("SUCCEEDED", write.getValue().task().getStatus());
        assertEquals("ANSWER", write.getValue().result().getResultType());
        assertEquals("READY", write.getValue().assistantStatus());
        assertEquals("H1", write.getValue().citations().get(0).getChunkId());
    }

    @Test
    void allIllegalCitationsRefuseUt10() {
        givenHits(hit("H1", 0.05, "依据原文一"));
        when(profileRepository.findByCode("RERANK_BGE_V2")).thenReturn(Optional.empty());
        when(aiGateway.answer(anyString(), anyList(), anyList())).thenReturn(new AnswerOutcome(
                "Kimi", "qa-v1", null, "答案文本",
                List.of(new CitationRef("NOT_EXIST", null),
                        new CitationRef("H1", "这段引用根本对不上原文")), false));

        QaService.AskResult result = service.ask(SESSION_ID, "问题？");

        assertEquals("REFUSED", result.status());
        verify(qaTxService).persist(any());
    }

    @Test
    void quotePrefixContainedIsAccepted() {
        givenHits(hit("H1", 0.05, "依据原文一，后续内容"));
        when(profileRepository.findByCode("RERANK_BGE_V2")).thenReturn(Optional.empty());
        when(aiGateway.answer(anyString(), anyList(), anyList())).thenReturn(new AnswerOutcome(
                "Kimi", "qa-v1", null, "答案",
                List.of(new CitationRef("H1", "依据原文一，后")), false)); // 前 20 字包含即可

        assertEquals("READY", service.ask(SESSION_ID, "问题？").status());
    }

    @Test
    void modelSelfRefusalBecomesRefused() {
        givenHits(hit("H1", 0.05, "依据"));
        when(profileRepository.findByCode("RERANK_BGE_V2")).thenReturn(Optional.empty());
        when(aiGateway.answer(anyString(), anyList(), anyList())).thenReturn(new AnswerOutcome(
                "Kimi", "qa-v1", null, "", List.of(), true));

        assertEquals("REFUSED", service.ask(SESSION_ID, "问题？").status());
    }

    @Test
    void timeoutWritesFailedMessageAndRetrows504() {
        givenHits(hit("H1", 0.05, "依据"));
        when(profileRepository.findByCode("RERANK_BGE_V2")).thenReturn(Optional.empty());
        when(aiGateway.answer(anyString(), anyList(), anyList())).thenThrow(
                new CidasBusinessException(ErrorCode.TIMEOUT, "ai-service 调用超时", true, java.util.Map.of()));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.ask(SESSION_ID, "问题？"));
        assertEquals(ErrorCode.TIMEOUT, e.errorCode());
        ArgumentCaptor<QaTxService.Write> write = ArgumentCaptor.forClass(QaTxService.Write.class);
        verify(qaTxService).persist(write.capture());
        assertEquals("FAILED", write.getValue().assistantStatus());
        assertTrue(write.getValue().task() == null, "FAILED 不写任务与结果");
    }

    @Test
    void rerankFailureDegradesToVectorOrderTake4() {
        givenHits(hit("H1", 0.05, "a"), hit("H2", 0.1, "b"), hit("H3", 0.15, "c"),
                hit("H4", 0.2, "d"), hit("H5", 0.25, "e"), hit("H6", 0.3, "f"));
        when(profileRepository.findByCode("RERANK_BGE_V2")).thenReturn(Optional.of(
                new AiModelProfile("P2", "RERANK_BGE_V2", "RERANK", "r", null, null, "A")));
        when(aiGateway.rerank(anyString(), anyList(), anyInt()))
                .thenThrow(new CidasBusinessException(ErrorCode.MODEL_FAILED, "rerank 挂", true, java.util.Map.of()));
        when(aiGateway.answer(anyString(), anyList(), anyList())).thenReturn(new AnswerOutcome(
                "Kimi", "qa-v1", null, "答案", List.of(new CitationRef("H1", null)), false));

        QaService.AskResult result = service.ask(SESSION_ID, "问题？");

        assertEquals("READY", result.status());
        ArgumentCaptor<List<SourceChunk>> evidence = ArgumentCaptor.forClass(List.class);
        verify(aiGateway).answer(anyString(), evidence.capture(), anyList());
        assertEquals(List.of("H1", "H2", "H3", "H4"),
                evidence.getValue().stream().map(SourceChunk::chunkId).toList());
    }

    @Test
    void rerankReordersEvidenceWhenAvailable() {
        givenHits(hit("H1", 0.05, "a"), hit("H2", 0.1, "b"), hit("H3", 0.15, "c"), hit("H4", 0.2, "d"));
        when(profileRepository.findByCode("RERANK_BGE_V2")).thenReturn(Optional.of(
                new AiModelProfile("P2", "RERANK_BGE_V2", "RERANK", "r", null, null, "A")));
        when(aiGateway.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of(
                new RerankHit(3, 0.9), new RerankHit(0, 0.5)));
        when(aiGateway.answer(anyString(), anyList(), anyList())).thenReturn(new AnswerOutcome(
                "Kimi", "qa-v1", null, "答案", List.of(new CitationRef("H4", null)), false));

        service.ask(SESSION_ID, "问题？");

        ArgumentCaptor<List<SourceChunk>> evidence = ArgumentCaptor.forClass(List.class);
        verify(aiGateway).answer(anyString(), evidence.capture(), anyList());
        assertEquals(List.of("H4", "H1"), evidence.getValue().stream().map(SourceChunk::chunkId).toList());
    }

    @Test
    void historyIsServerSideLastSixReadyMessages() {
        givenHits(hit("H1", 0.05, "依据"));
        when(profileRepository.findByCode("RERANK_BGE_V2")).thenReturn(Optional.empty());
        when(qaRepository.findMessages(SESSION_ID)).thenReturn(List.of(
                new QaMessage("M1", SESSION_ID, "USER", "第一问", null, 1, "READY"),
                new QaMessage("M2", SESSION_ID, "ASSISTANT", "第一答", null, 2, "READY"),
                new QaMessage("M3", SESSION_ID, "ASSISTANT", "拒答", null, 3, "REFUSED")));
        when(aiGateway.answer(anyString(), anyList(), anyList())).thenReturn(new AnswerOutcome(
                "Kimi", "qa-v1", null, "答案", List.of(new CitationRef("H1", null)), false));

        service.ask(SESSION_ID, "第二问");

        ArgumentCaptor<List<HistoryMessage>> history = ArgumentCaptor.forClass(List.class);
        verify(aiGateway).answer(eq("第二问"), anyList(), history.capture());
        assertEquals(List.of("第一问", "第一答"), history.getValue().stream()
                .map(HistoryMessage::content).toList());
    }

    @Test
    void questionValidationAndSessionOwnership() {
        when(profileRepository.findByCode("RERANK_BGE_V2")).thenReturn(Optional.empty());
        assertThrows(CidasBusinessException.class, () -> service.ask(SESSION_ID, " "));
        assertThrows(CidasBusinessException.class, () -> service.ask(SESSION_ID, "字".repeat(1001)));

        QaSession foreign = new QaSession(SESSION_ID, DOC_ID, "OTHER", "t", "A");
        when(qaRepository.findSession(SESSION_ID)).thenReturn(Optional.of(foreign));
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.ask(SESSION_ID, "问题？"));
        assertEquals(ErrorCode.NOT_FOUND, e.errorCode());
    }

    @Test
    void archivedSessionRejectedOnAsk() {
        when(qaRepository.findSession(SESSION_ID)).thenReturn(Optional.of(
                new QaSession(SESSION_ID, DOC_ID, "U1", "t", "C")));
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.ask(SESSION_ID, "问题？"));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
    }

    @Test
    void qaRequiresDocumentReadCombinedWithAiQa() {
        // 1105 审查 P2-3：QA 服务层组合同时校验 DOCUMENT_READ + AI_QA
        AuthContext.set(new UserPrincipal("U1", "stu", null, Set.of("STUDENT"), Set.of("AI_QA"), Set.of()));
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.createSession(DOC_ID, null));
        assertEquals(ErrorCode.FORBIDDEN, e.errorCode());
    }

    @Test
    void createSessionReusesActiveSession() {
        when(qaRepository.findActiveSession(DOC_ID, "U1")).thenReturn(Optional.of(
                new QaSession(SESSION_ID, DOC_ID, "U1", "既有会话", "A")));

        QaService.SessionView view = service.createSession(DOC_ID, null);

        assertEquals(SESSION_ID, view.sessionId());
        verify(qaRepository, never()).insertSession(any());
    }
}
