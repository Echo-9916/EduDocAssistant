package com.cidas.edudoc.ai.infrastructure;

import com.cidas.edudoc.ai.application.AiGateway;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.TraceContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ai-service 内部接口客户端（红线：HTTP 客户端只出现在 infrastructure）。
 * 超时分级：embed/rerank 短、answer 按问答预算、生成类长；透传 X-Trace-Id 与服务间鉴权头 X-Internal-Token。
 * 启动健壮性（1104 教训）：构造不触网，缺 internal-token 时调用才 50301 干净降级，Bean 不炸启动。
 * ponytail: 熔断不做，重试在 ai-service 侧一次 + 任务层 retry_count，需要时引 Resilience4j。
 */
@Slf4j
@Component
public class AiServiceClient implements AiGateway {

    private final AiServiceProperties properties;
    private final ObjectMapper mapper;
    private final RestClient shortClient;
    private final RestClient longClient;
    private final RestClient answerClient;

    public AiServiceClient(AiServiceProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.shortClient = buildClient(properties.shortTimeoutMs());
        this.longClient = buildClient(properties.longTimeoutMs());
        this.answerClient = buildClient(properties.answerTimeoutMs());
    }

    private RestClient buildClient(long timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    if (properties.internalToken() != null && !properties.internalToken().isBlank()) {
                        request.getHeaders().set("X-Internal-Token", properties.internalToken());
                    }
                    String traceId = TraceContext.get();
                    if (traceId != null && !traceId.isBlank()) {
                        request.getHeaders().set(TraceHeaders.NAME, traceId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    /** trace 头名与入站 TraceIdFilter 同源。 */
    private static final class TraceHeaders {
        static final String NAME = "X-Trace-Id";
    }

    @Override
    public EmbedOutcome embed(List<String> texts) {
        JsonNode resp = post("/v1/embed", Map.of("texts", texts), shortClient);
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode vector : resp.path("vectors")) {
            float[] row = new float[vector.size()];
            for (int i = 0; i < row.length; i++) {
                row[i] = (float) vector.get(i).asDouble();
            }
            vectors.add(row);
        }
        if (vectors.size() != texts.size()) {
            throw failed("embed 返回向量数量不对齐：请求 " + texts.size() + "，返回 " + vectors.size(), true);
        }
        return new EmbedOutcome(resp.path("model").asText(null), vectors);
    }

    @Override
    public SummarizeOutcome summarize(List<SourceChunk> chunks) {
        JsonNode resp = post("/v1/summarize",
                Map.of("chunks", chunks.stream().map(c -> Map.of("chunkId", c.chunkId(), "text", c.text())).toList()),
                longClient);
        List<SummarySegment> segments = new ArrayList<>();
        for (JsonNode segment : resp.path("segments")) {
            List<String> chunkIds = new ArrayList<>();
            segment.path("chunkIds").forEach(node -> chunkIds.add(node.asText()));
            segments.add(new SummarySegment(segment.path("text").asText(""), chunkIds));
        }
        return new SummarizeOutcome(resp.path("model").asText(null), resp.path("promptVersion").asText(null),
                usage(resp), segments);
    }

    @Override
    public KeypointOutcome extractKeypoints(List<SourceChunk> chunks) {
        JsonNode resp = post("/v1/extract-keypoints",
                Map.of("chunks", chunks.stream().map(c -> Map.of("chunkId", c.chunkId(), "text", c.text())).toList()),
                longClient);
        List<Keypoint> keypoints = new ArrayList<>();
        for (JsonNode node : resp.path("keypoints")) {
            keypoints.add(new Keypoint(node.path("text").asText(""), node.path("chunkId").asText(null)));
        }
        return new KeypointOutcome(resp.path("model").asText(null), resp.path("promptVersion").asText(null),
                usage(resp), keypoints);
    }

    @Override
    public AnswerOutcome answer(String question, List<SourceChunk> evidence, List<HistoryMessage> history) {
        JsonNode resp = post("/v1/answer", Map.of(
                "question", question,
                "evidence", evidence.stream().map(c -> Map.of("chunkId", c.chunkId(), "text", c.text())).toList(),
                "history", history.stream().map(h -> Map.of("role", h.role(), "content", h.content())).toList(),
                "maxTokens", 900), answerClient);
        List<CitationRef> citations = new ArrayList<>();
        for (JsonNode node : resp.path("citations")) {
            citations.add(new CitationRef(node.path("chunkId").asText(null), node.path("quote").asText(null)));
        }
        return new AnswerOutcome(resp.path("model").asText(null), resp.path("promptVersion").asText(null),
                usage(resp), resp.path("answer").asText(""), citations, resp.path("refused").asBoolean(false));
    }

    @Override
    public PolishOutcome polish(List<String> paragraphs) {
        JsonNode resp = post("/v1/polish", Map.of("paragraphs", paragraphs), longClient);
        List<String> polished = new ArrayList<>();
        resp.path("polished").forEach(node -> polished.add(node.path("text").asText(
                node.isTextual() ? node.asText() : "")));
        if (polished.size() != paragraphs.size()) {
            throw failed("polish 返回段落数量不对齐：请求 " + paragraphs.size() + "，返回 " + polished.size(), true);
        }
        return new PolishOutcome(resp.path("model").asText(null), resp.path("promptVersion").asText(null),
                usage(resp), polished);
    }

    @Override
    public List<RerankHit> rerank(String query, List<String> documents, int topN) {
        JsonNode resp = post("/v1/rerank",
                Map.of("query", query, "documents", documents, "topN", topN), shortClient);
        List<RerankHit> hits = new ArrayList<>();
        for (JsonNode node : resp.path("results")) {
            hits.add(new RerankHit(node.path("index").asInt(), node.path("relevanceScore").asDouble()));
        }
        return hits;
    }

    private JsonNode post(String path, Map<String, Object> body, RestClient client) {
        if (properties.internalToken() == null || properties.internalToken().isBlank()) {
            throw new CidasBusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "AI 服务间密钥未配置（EDUDOC_AI_INTERNAL_TOKEN）");
        }
        String json;
        try {
            json = mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw failed("AI 请求序列化失败：" + e.getMessage(), false);
        }
        try {
            JsonNode resp = client.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(JsonNode.class);
            if (resp == null) {
                throw failed("ai-service 空响应：" + path, true);
            }
            return resp;
        } catch (RestClientResponseException e) {
            String errBody = trim(e.getResponseBodyAsString());
            log.warn("ai-service 返回异常状态：path={} status={} body={}", path, e.getStatusCode(), errBody);
            // 1105 审查 P2-1：优先解析 ai-service 结构化 code，再以 HTTP status 兜底
            if (e.getStatusCode().value() == 504 || "TIMEOUT".equals(upstreamCode(errBody))) {
                throw new CidasBusinessException(ErrorCode.TIMEOUT,
                        "ai-service 超时：" + path, true, Map.of("path", path));
            }
            boolean retryable = e.getStatusCode().is5xxServerError();
            throw new CidasBusinessException(ErrorCode.MODEL_FAILED,
                    "模型服务失败：" + e.getStatusCode() + " " + errBody, retryable, Map.of("path", path));
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new CidasBusinessException(ErrorCode.TIMEOUT,
                        "ai-service 调用超时：" + path, true, Map.of("path", path));
            }
            throw new CidasBusinessException(ErrorCode.MODEL_FAILED,
                    "ai-service 不可达：" + e.getMessage(), true, Map.of("path", path));
        } catch (RestClientException e) {
            // 读超时/IO 异常也可能包在响应体抽取的 RestClientException 里，按 cause 链归级
            if (hasCause(e, SocketTimeoutException.class)) {
                throw new CidasBusinessException(ErrorCode.TIMEOUT,
                        "ai-service 调用超时：" + path, true, Map.of("path", path));
            }
            if (hasCause(e, java.io.IOException.class)) {
                throw new CidasBusinessException(ErrorCode.MODEL_FAILED,
                        "ai-service 不可达：" + e.getMessage(), true, Map.of("path", path));
            }
            throw new CidasBusinessException(ErrorCode.MODEL_FAILED,
                    "ai-service 响应异常：" + e.getMessage(), false, Map.of("path", path));
        }
    }

    /** 从 ai-service 结构化错误体 {code:…} 提取 code；解析失败返回 null（不臆断）。 */
    private String upstreamCode(String body) {
        if (body == null || !body.contains("\"code\"")) {
            return null;
        }
        try {
            return mapper.readTree(body).path("code").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasCause(Throwable e, Class<?> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static CidasBusinessException failed(String message, boolean retryable) {
        return new CidasBusinessException(ErrorCode.MODEL_FAILED, message, retryable, Map.of());
    }

    private static TokenUsage usage(JsonNode resp) {
        JsonNode node = resp.path("usage");
        JsonNode input = node.path("inputTokens");
        JsonNode output = node.path("outputTokens");
        return new TokenUsage(input.isNumber() ? input.asInt() : null, output.isNumber() ? output.asInt() : null);
    }

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
}
