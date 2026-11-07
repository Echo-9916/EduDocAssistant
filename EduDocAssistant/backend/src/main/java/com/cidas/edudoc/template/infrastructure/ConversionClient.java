package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.ai.infrastructure.AiServiceProperties;
import com.cidas.edudoc.common.api.TraceContext;
import com.cidas.edudoc.template.application.ConvertEngineGateway;
import com.cidas.edudoc.template.application.ConversionEngineException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/**
 * ai-service `/v1/convert` 客户端（D-06/J2）：multipart 上传源文件 + targetFormat，成功回二进制；
 * 透传 X-Internal-Token/X-Trace-Id。启动健壮性沿用 AiServiceClient 口径：构造不触网，
 * 缺 internal-token 或引擎不可达时干净抛 ConversionEngineException(ENGINE_UNAVAILABLE)。
 */
@Slf4j
@Component
public class ConversionClient implements ConvertEngineGateway {

    private final AiServiceProperties aiProperties;
    private final ConversionProperties properties;
    private final ObjectMapper mapper;
    private final RestClient client;

    /** 终审 I2：读超时=引擎预算+15s 余量——引擎自身超时先行返回结构化 504 时，后端按 TIMEOUT 记账而非误报不可达。 */
    private static final long READ_TIMEOUT_MARGIN_MS = 15_000;

    public ConversionClient(AiServiceProperties aiProperties, ConversionProperties properties,
                            ObjectMapper mapper) {
        this.aiProperties = aiProperties;
        this.properties = properties;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(3000, Math.max(100, properties.timeoutMs()))));
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs() + READ_TIMEOUT_MARGIN_MS));
        this.client = RestClient.builder()
                .baseUrl(aiProperties.baseUrl())
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    if (aiProperties.internalToken() != null && !aiProperties.internalToken().isBlank()) {
                        request.getHeaders().set("X-Internal-Token", aiProperties.internalToken());
                    }
                    String traceId = TraceContext.get();
                    if (traceId != null && !traceId.isBlank()) {
                        request.getHeaders().set("X-Trace-Id", traceId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    @Override
    public ConvertResult convert(byte[] sourceBytes, String fileName, String targetFormat) {
        if (aiProperties.internalToken() == null || aiProperties.internalToken().isBlank()) {
            throw new ConversionEngineException("ENGINE_UNAVAILABLE",
                    "服务间密钥未配置（EDUDOC_AI_INTERNAL_TOKEN）");
        }
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", sourceBytes).filename(fileName);
        body.part("targetFormat", targetFormat);
        try {
            byte[] content = client.post().uri("/v1/convert")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(byte[].class);
            if (content == null || content.length == 0) {
                throw new ConversionEngineException("CORRUPTED", "转换返回空内容");
            }
            String mime = "PDF".equals(targetFormat)
                    ? "application/pdf"
                    : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            String outName = fileName.replaceAll("\\.[^.]+$", "") + ("PDF".equals(targetFormat) ? ".pdf" : ".docx");
            return new ConvertResult(content, mime, outName);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw mapFailure(e);
        }
    }

    /** 终审 I2：失败映射单测入口——SocketTimeout=TIMEOUT，其余网络=ENGINE_UNAVAILABLE，上游体保 code。 */
    static ConversionEngineException mapFailure(Exception e) {
        if (e instanceof RestClientResponseException response) {
            try {
                JsonNode node = JSON.readTree(response.getResponseBodyAsString());
                return new ConversionEngineException(node.path("code").asText("ENGINE_UNAVAILABLE"),
                        node.path("message").asText(e.getMessage()));
            } catch (Exception parse) {
                return new ConversionEngineException("ENGINE_UNAVAILABLE",
                        "转换服务响应异常（HTTP " + response.getStatusCode().value() + "）");
            }
        }
        Throwable cause = e.getCause();
        if (cause instanceof java.net.SocketTimeoutException) {
            return new ConversionEngineException("TIMEOUT", "转换超时（引擎预算 "
                    + "内未完成）");
        }
        return new ConversionEngineException("ENGINE_UNAVAILABLE", "转换服务不可达");
    }

    private static final ObjectMapper JSON = new ObjectMapper();
}
