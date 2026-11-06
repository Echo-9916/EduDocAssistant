package com.cidas.edudoc.ai.application.impl;

import com.cidas.edudoc.ai.application.AiGateway;
import com.cidas.edudoc.ai.application.AiGateway.AnswerOutcome;
import com.cidas.edudoc.ai.application.AiGateway.CitationRef;
import com.cidas.edudoc.ai.application.AiGateway.HistoryMessage;
import com.cidas.edudoc.ai.application.AiGateway.RerankHit;
import com.cidas.edudoc.ai.application.AiGateway.SourceChunk;
import com.cidas.edudoc.ai.application.QaService;
import com.cidas.edudoc.ai.application.QaTxService;
import com.cidas.edudoc.ai.application.EmbedWorker;
import com.cidas.edudoc.ai.infrastructure.AiCitation;
import com.cidas.edudoc.ai.infrastructure.AiModelProfile;
import com.cidas.edudoc.ai.infrastructure.AiModelProfileRepository;
import com.cidas.edudoc.ai.infrastructure.AiResult;
import com.cidas.edudoc.ai.infrastructure.AiResultRepository;
import com.cidas.edudoc.ai.infrastructure.AiTask;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.SearchHit;
import com.cidas.edudoc.ai.infrastructure.QaMessage;
import com.cidas.edudoc.ai.infrastructure.QaProperties;
import com.cidas.edudoc.ai.infrastructure.QaRepository;
import com.cidas.edudoc.ai.infrastructure.QaSession;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 问答同步实现（C-06，§3.2.2）：阈值与全部非法引用都走 REFUSED（业务成功，双消息落库）；
 * 生成失败/超时落 FAILED 消息后按错误码上抛（50401→HTTP 504），不写伪造答案。
 * 连续追问历史由服务端取会话最近 6 条拼 prompt，前端不传（防注入）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaServiceImpl implements QaService {

    static final int TOP_K = 6;
    static final int RERANK_TAKE = 4;
    static final int HISTORY_SIZE = 6;
    static final int MAX_QUESTION_CHARS = 1000;
    static final String REFUSAL_TEXT = "未在文档中找到依据，无法回答该问题。";

    private final DocumentRepository documentRepository;
    private final ChunkEmbeddingRepository embeddingRepository;
    private final AiModelProfileRepository profileRepository;
    private final AiGateway aiGateway;
    private final QaRepository qaRepository;
    private final QaTxService qaTxService;
    private final QaProperties qaProperties;
    private final AiResultRepository aiResultRepository;
    private final ObjectMapper objectMapper;

    @Override
    public SessionView createSession(String documentId, String title) {
        UserPrincipal principal = AuthContext.require();
        assertQaPermissions(principal);
        Document doc = requireVisible(documentId, principal);
        Optional<QaSession> active = qaRepository.findActiveSession(doc.getId(), principal.userId());
        if (active.isPresent()) {
            return toView(active.get()); // (document,user) 活跃会话复用（uk 无，服务层保证）
        }
        QaSession session = new QaSession(Ids.hex16(), doc.getId(), principal.userId(),
                title == null || title.isBlank() ? doc.getTitle() + " 问答" : clip(title, 200), "A");
        qaRepository.insertSession(session);
        return toView(session);
    }

    @Override
    public List<SessionView> listSessions(String documentId) {
        UserPrincipal principal = AuthContext.require();
        assertQaPermissions(principal);
        Document doc = requireVisible(documentId, principal);
        return qaRepository.findSessions(doc.getId(), principal.userId()).stream()
                .map(this::toView).toList();
    }

    @Override
    public AskResult ask(String sessionId, String question) {
        UserPrincipal principal = AuthContext.require();
        assertQaPermissions(principal);
        QaSession session = requireOwnActiveSession(sessionId, principal);
        if (question == null || question.isBlank() || question.strip().length() > MAX_QUESTION_CHARS) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "问题需为 1-1000 字符");
        }
        String q = question.strip();
        Document doc = requireVisible(session.getDocumentId(), principal);

        // P-05 端到端统一预算（1105 审查 P1-3）：逐段检查剩余时间并把上限传给每次外部调用。
        // 1106 复审 P2-4：embed/search/rerank/answer 任一阶段超时或模型失败，统一在此写 FAILED 双消息
        // （拒答 REFUSED 是业务成功，走 refuse/refuseOutcome 正常返回，不落 FAILED）。
        long deadline = System.nanoTime()
                + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(1, qaProperties.budgetMs()));
        try {
            return runQaPipeline(sessionId, session, doc, q, deadline);
        } catch (CidasBusinessException e) {
            qaTxService.persist(new QaTxService.Write(sessionId, q,
                    "问答失败：" + e.getMessage(), "FAILED", null, null, List.of()));
            throw e;
        }
    }

    private AskResult runQaPipeline(String sessionId, QaSession session, Document doc, String q, long deadline) {
        AiModelProfile embedProfile = profileRepository.findByCode(EmbedWorker.EMBED_PROFILE_CODE)
                .filter(p -> "A".equals(p.getStatus()) && p.getEmbeddingDimension() != null)
                .orElseThrow(() -> new CidasBusinessException(
                        ErrorCode.SERVICE_UNAVAILABLE, "嵌入模型档案缺失或未启用"));
        long embedBudget = remainingOrThrow(deadline);
        List<float[]> vectors = aiGateway.embed(List.of(q), embedBudget).vectors();
        if (vectors.isEmpty() || vectors.get(0).length != embedProfile.getEmbeddingDimension()) {
            return refuse(session, q, "问题向量维度不符");
        }
        List<SearchHit> topHits = searchEvidence(doc, embedProfile, vectors.get(0));
        // 阈值判据在 topK=6 全集上算（1105 冒烟：分数若在 rerank 后子集上算，排序波动会翻转拒答判定）
        if (topHits.isEmpty() || evidenceScore(topHits) < qaProperties.evidenceThreshold()) {
            return refuse(session, q, "证据不足（阈值 " + qaProperties.evidenceThreshold() + "）");
        }
        long searchBudget = remainingOrThrow(deadline);
        List<SearchHit> hits = rerankTake(q, topHits, searchBudget);

        List<HistoryMessage> history = history(sessionId);
        long answerBudget = remainingOrThrow(deadline);
        AnswerOutcome outcome = aiGateway.answer(q, hits.stream()
                .map(h -> new SourceChunk(h.chunkId(), h.contentText())).toList(), history, answerBudget);
        List<CitationView> citations = outcome.refused() ? List.of()
                : validateCitations(outcome.citations(), hits);
        if (outcome.refused() || citations.isEmpty()) {
            // §3.2.2 两处 REFUSED 分支 + UT-10 全部非法引用 → 拒答
            return refuseOutcome(sessionId, q, outcome);
        }
        return persistAnswer(session, doc, q, outcome, citations);
    }

    @Override
    public List<MessageView> messages(String sessionId) {
        UserPrincipal principal = AuthContext.require();
        assertQaPermissions(principal);
        requireOwnActiveOrClosedSession(sessionId, principal);
        return qaRepository.findMessages(sessionId).stream().map(m -> {
            List<CitationView> citations = "ASSISTANT".equals(m.getRole()) && m.getAiTaskId() != null
                    ? citationsOfTask(m.getAiTaskId()) : List.of();
            return new MessageView(m.getId(), m.getRole(), m.getContentText(),
                    m.getStatus(), m.getSequenceNo(), citations);
        }).toList();
    }

    // ---------- 检索与阈值 ----------

    /** 剩余预算（扣 200ms 安全余量给下一段）；不足即抛 TIMEOUT，FAILED 双消息由 ask 外层统一落库（P2-4）。 */
    private long remainingOrThrow(long deadlineNanos) {
        long remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000 - 200;
        if (remainingMs < 300) {
            throw new CidasBusinessException(ErrorCode.TIMEOUT,
                    "问答端到端预算耗尽", true, Map.of("stage", "deadline"));
        }
        return remainingMs;
    }

    /** §3.2.2 第一步：版本内向量检索 topK=6（本地 SQL，无外部预算）。 */
    private List<SearchHit> searchEvidence(Document doc, AiModelProfile profile, float[] vector) {
        return embeddingRepository.searchByVersion(doc.getCurrentVersionId(),
                profile.getId(), EmbedWorker.toVectorLiteral(vector),
                profile.getEmbeddingDimension(), TOP_K);
    }

    /** §3.2.2 第二步：rerank take(4)；未配置 RERANK 档案或调用失败 → 降级向量序 take(4)（R2）。 */
    private List<SearchHit> rerankTake(String question, List<SearchHit> hits, long rerankBudgetMs) {
        int take = Math.min(RERANK_TAKE, hits.size());
        Optional<AiModelProfile> rerankProfile = profileRepository.findByCode("RERANK_BGE_V2")
                .filter(p -> "A".equals(p.getStatus()));
        if (rerankProfile.isEmpty()) {
            return hits.subList(0, take);
        }
        try {
            List<RerankHit> ranked = aiGateway.rerank(question,
                    hits.stream().map(SearchHit::contentText).toList(), take, rerankBudgetMs);
            List<SearchHit> reordered = ranked.stream()
                    .filter(h -> h.index() >= 0 && h.index() < hits.size())
                    .map(h -> hits.get(h.index()))
                    .limit(take)
                    .toList();
            return reordered.isEmpty() ? hits.subList(0, take) : reordered;
        } catch (Exception e) {
            // ponytail: R2——rerank 失败降级向量序，不阻塞问答主链路
            log.warn("rerank 失败，降级向量序 take({})：{}", take, e.getMessage());
            return hits.subList(0, take);
        }
    }

    /** 归一余弦相似度：COSINE 距离定义域 [0,2] → score=max(0,1-d/2)∈[0,1]；
     *  旧式 1-min(d) 在 d>1 时恒 ≤0，阈值形同虚设全部拒答（1105 冒烟复测校准错误，探针实证）。 */
    private static double evidenceScore(List<SearchHit> hits) {
        double minDistance = hits.stream().mapToDouble(SearchHit::distance).min().orElse(2.0);
        return Math.max(0.0, 1.0 - minDistance / 2.0);
    }

    // ---------- 引用硬校验（R9） ----------

    static List<CitationView> validateCitations(List<CitationRef> refs, List<SearchHit> evidence) {
        Map<String, SearchHit> byId = new LinkedHashMap<>();
        evidence.forEach(h -> byId.put(h.chunkId(), h));
        List<CitationView> valid = new ArrayList<>();
        for (CitationRef ref : refs) {
            SearchHit hit = ref.chunkId() == null ? null : byId.get(ref.chunkId());
            if (hit == null) {
                continue; // chunkId 不属本证据集 → 非法
            }
            String quote = ref.quote();
            if (quote != null && !quote.isBlank()) {
                String probe = quote.length() > 20 ? quote.substring(0, 20) : quote;
                String text = hit.contentText() == null ? "" : hit.contentText().replace("\n", "");
                String probeFlat = probe.replace("\n", "");
                if (!text.contains(probeFlat)) {
                    continue; // 引用文本与分块内容核对不上（前 20 字符包含即可）
                }
            }
            valid.add(new CitationView(hit.chunkId(), hit.pageNo(), hit.charStart(),
                    hit.charEnd(), quote == null || quote.isBlank() ? hit.contentText() : quote));
        }
        return valid;
    }

    // ---------- 落库与视图 ----------

    private AskResult refuse(QaSession session, String question, String reason) {
        log.info("问答拒答 sessionId={} reason={}", session.getId(), reason);
        return refuseOutcome(session.getId(), question, null);
    }

    private AskResult refuseOutcome(String sessionId, String question, AnswerOutcome outcome) {
        QaTxService.Written written = qaTxService.persist(new QaTxService.Write(
                sessionId, question, REFUSAL_TEXT, "REFUSED", null, null, List.of()));
        return new AskResult(sessionId, written.questionMessageId(), written.answerMessageId(),
                "REFUSED", REFUSAL_TEXT, List.of());
    }

    private AskResult persistAnswer(QaSession session, Document doc, String question,
                                    AnswerOutcome outcome, List<CitationView> citations) {
        String taskId = Ids.hex16();
        String resultId = Ids.hex16();
        AiTask task = new AiTask(taskId, doc.getId(), doc.getCurrentVersionId(), "QA",
                "SUCCEEDED", 5, null, "{\"source\":\"qa-sync\"}",
                AuthContext.require().userId(), 100, null, null, 0);
        List<AiCitation> rows = new ArrayList<>();
        int rank = 1;
        for (CitationView c : citations) {
            rows.add(new AiCitation(Ids.hex16(), resultId, c.chunkId(), c.pageNo(),
                    c.charStart(), c.charEnd(), AiResultRepository.clipQuote(c.quoteText()), rank++));
        }
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("answer", outcome.answer());
        structured.put("citations", citations);
        AiResult result = new AiResult(resultId, taskId, "ANSWER", outcome.answer(),
                writeJson(structured), outcome.model() == null ? "unknown" : outcome.model(),
                outcome.promptVersion() == null ? "unknown" : outcome.promptVersion(),
                outcome.usage() == null ? null : outcome.usage().inputTokens(),
                outcome.usage() == null ? null : outcome.usage().outputTokens());
        QaTxService.Written written = qaTxService.persist(new QaTxService.Write(
                session.getId(), question, outcome.answer(), "READY", task, result, rows));
        return new AskResult(session.getId(), written.questionMessageId(), written.answerMessageId(),
                "READY", outcome.answer(), citations);
    }

    private List<CitationView> citationsOfTask(String taskId) {
        return aiResultRepository.findLatestByTask(taskId)
                .map(r -> aiResultRepository.findCitations(r.getId()).stream()
                        .map(c -> new CitationView(c.getChunkId(), c.getPageNo(),
                                c.getCharStart(), c.getCharEnd(), c.getQuoteText()))
                        .toList())
                .orElse(List.of());
    }

    private List<HistoryMessage> history(String sessionId) {
        List<QaMessage> all = qaRepository.findMessages(sessionId);
        return all.stream()
                .filter(m -> !"SYSTEM".equals(m.getRole()) && "READY".equals(m.getStatus()))
                .sorted(Comparator.comparingInt(QaMessage::getSequenceNo).reversed())
                .limit(HISTORY_SIZE)
                .map(m -> new HistoryMessage("USER".equals(m.getRole()) ? "user" : "assistant",
                        m.getContentText()))
                .toList()
                .reversed();
    }

    // ---------- 归属与校验 ----------

    /** 1105 审查 P2-3：AI_QA 与 DOCUMENT_READ 权限点组合（服务层校验，不依赖注解锁死）。 */
    private static void assertQaPermissions(UserPrincipal principal) {
        for (String permission : List.of(com.cidas.edudoc.access.domain.PermissionCode.AI_QA,
                com.cidas.edudoc.access.domain.PermissionCode.DOCUMENT_READ)) {
            if (!principal.permissions().contains(permission)) {
                throw new CidasBusinessException(ErrorCode.FORBIDDEN, "权限点不足：" + permission);
            }
        }
    }

    private Document requireVisible(String documentId, UserPrincipal principal) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND);
        }
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND));
        if (!principal.userId().equalsIgnoreCase(doc.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在或不可见");
        }
        if (!"READY".equals(doc.getStatus())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "文档尚未就绪");
        }
        return doc;
    }

    private QaSession requireOwnActiveSession(String sessionId, UserPrincipal principal) {
        QaSession session = requireOwnSession(sessionId, principal);
        if (!"A".equals(session.getStatus())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "会话已归档，请新建会话");
        }
        return session;
    }

    private QaSession requireOwnActiveOrClosedSession(String sessionId, UserPrincipal principal) {
        return requireOwnSession(sessionId, principal);
    }

    private QaSession requireOwnSession(String sessionId, UserPrincipal principal) {
        if (!Ids.isHex16(sessionId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND);
        }
        QaSession session = qaRepository.findSession(sessionId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND));
        if (!principal.userId().equalsIgnoreCase(session.getUserId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "会话不存在或不可见");
        }
        return session;
    }

    private SessionView toView(QaSession session) {
        return new SessionView(session.getId(), session.getDocumentId(), session.getTitle(),
                session.getStatus());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new CidasBusinessException(ErrorCode.INTERNAL_ERROR, "答案序列化失败");
        }
    }

    private static String clip(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
