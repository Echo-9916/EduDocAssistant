package com.cidas.edudoc.ai.infrastructure;

import com.cidas.edudoc.ai.application.AiGateway;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.TraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-02 服务间客户端：鉴权头、trace 透传、超时/不可达映射 50201/50401、缺 token 50301 干净降级。
 * 用 JDK HttpServer 真起真断（零 mock 链、零新依赖）。
 */
class AiServiceClientTest {

    private HttpServer server;
    private volatile String lastPath;
    private volatile String lastBody;
    private volatile Map<String, String> lastHeaders;
    private volatile int responseStatus = 200;
    private volatile String responseJson = "{}";
    private volatile long delayMs;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().getPath();
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastHeaders = Map.of(
                    "token", String.valueOf(exchange.getRequestHeaders().getFirst("X-Internal-Token")),
                    "trace", String.valueOf(exchange.getRequestHeaders().getFirst("X-Trace-Id")));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                exchange.getResponseBody().write(body);
                exchange.getResponseBody().close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        TraceContext.clear();
    }

    private AiServiceClient client(String token, long shortTimeoutMs) {
        return new AiServiceClient(new AiServiceProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), token,
                shortTimeoutMs, 5000, 5000), new ObjectMapper());
    }

    @Test
    void embedSendsTokenAndTraceAndParsesVectors() {
        TraceContext.set("trace-abc");
        responseJson = "{\"model\":\"bge-m3\",\"vectors\":[[0.5,0.25],[0.75,1.0]]}";

        AiGateway.EmbedOutcome outcome = client("tok123", 5000).embed(List.of("甲", "乙"));

        assertEquals("bge-m3", outcome.model());
        assertEquals(2, outcome.vectors().size());
        assertEquals(0.25f, outcome.vectors().get(0)[1], 1e-6);
        assertEquals("/v1/embed", lastPath);
        assertEquals("tok123", lastHeaders.get("token"));
        assertEquals("trace-abc", lastHeaders.get("trace"));
        assertTrue(lastBody.contains("甲") || lastBody.contains("\\u7532"), "请求体应含待嵌入文本：" + lastBody);
    }

    @Test
    void embedMismatchedVectorCountIsRetryableModelFailed() {
        responseJson = "{\"model\":\"bge-m3\",\"vectors\":[[0.1]]}";

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> client("tok", 5000).embed(List.of("a", "b")));
        assertEquals(ErrorCode.MODEL_FAILED, e.errorCode());
        assertTrue(e.retryable());
    }

    @Test
    void readTimeoutMapsTo50401Retryable() throws Exception {
        delayMs = 800;

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> client("tok", 200).embed(List.of("a")));
        assertEquals(ErrorCode.TIMEOUT, e.errorCode());
        assertTrue(e.retryable());
    }

    @Test
    void unreachableMapsTo50201Retryable() {
        server.stop(0);

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> client("tok", 2000).embed(List.of("a")));
        assertEquals(ErrorCode.MODEL_FAILED, e.errorCode());
        assertTrue(e.retryable());
    }

    @Test
    void upstream504StructuredTimeoutMapsTo50401() {
        // 1105 审查 P2-1：ai-service 返回 504+code=TIMEOUT 时不得塌缩为 50201
        responseStatus = 504;
        responseJson = "{\"code\":\"TIMEOUT\",\"message\":\"chat/completions 调用超时\",\"provider\":\"x\",\"model\":\"m\",\"elapsedMs\":4600}";

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> client("tok", 2000).embed(List.of("a")));
        assertEquals(ErrorCode.TIMEOUT, e.errorCode());
        assertTrue(e.retryable());
    }

    @Test
    void upstream4xxMapsToNonRetryableModelFailed() {
        responseStatus = 401;
        responseJson = "{\"code\":\"MODEL_FAILED\",\"message\":\"bad token\"}";

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> client("tok", 2000).embed(List.of("a")));
        assertEquals(ErrorCode.MODEL_FAILED, e.errorCode());
        assertTrue(!e.retryable());
    }

    @Test
    void missingTokenDegradesTo50301WithoutNetwork() {
        // 缺 token：Bean 构造本身不炸（启动健壮性红线），调用才降级 50301，且不触网
        AiServiceClient c = client("", 1000);
        CidasBusinessException e = assertThrows(CidasBusinessException.class, () -> c.embed(List.of("a")));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, e.errorCode());
        assertTrue(lastPath == null, "缺 token 时不应发起 HTTP 调用");
    }

    @Test
    void summarizeParsesSegmentsPromptVersionAndUsage() {
        responseJson = """
                {"model":"Kimi-K2","promptVersion":"sum-v1","usage":{"inputTokens":100,"outputTokens":40},
                 "segments":[{"text":"第一段摘要","chunkIds":["C1","C2"]},{"text":"第二段摘要","chunkIds":["C2"]}]}""";

        AiGateway.SummarizeOutcome outcome = client("tok", 5000)
                .summarize(List.of(new AiGateway.SourceChunk("C1", "正文一"), new AiGateway.SourceChunk("C2", "正文二")));

        assertEquals("sum-v1", outcome.promptVersion());
        assertEquals(2, outcome.segments().size());
        assertEquals(List.of("C1", "C2"), outcome.segments().get(0).chunkIds());
        assertEquals(100, outcome.usage().inputTokens());
        assertEquals(40, outcome.usage().outputTokens());
        assertEquals("/v1/summarize", lastPath);
    }

    @Test
    void answerParsesRefusedFlagAndCitations() {
        responseJson = """
                {"model":"Kimi-K2","promptVersion":"qa-v1","refused":false,
                 "answer":"依据[1]作答","citations":[{"chunkId":"C1","quote":"原文片段"}]}""";

        AiGateway.AnswerOutcome outcome = client("tok", 5000).answer("问？",
                List.of(new AiGateway.SourceChunk("C1", "答")), List.of());

        assertTrue(!outcome.refused());
        assertEquals("C1", outcome.citations().get(0).chunkId());
        assertEquals(900, lastBody.contains("\"maxTokens\":900") ? 900 : -1, "请求需含 maxTokens=900");
    }

    @Test
    void rerankReturnsOrderedHits() {
        responseJson = "{\"results\":[{\"index\":2,\"relevanceScore\":0.9},{\"index\":0,\"relevanceScore\":0.4}]}";

        List<AiGateway.RerankHit> hits = client("tok", 5000).rerank("问", List.of("a", "b", "c"), 2);

        assertEquals(2, hits.size());
        assertEquals(2, hits.get(0).index());
        assertEquals(0.4, hits.get(1).score(), 1e-9);
    }
}
