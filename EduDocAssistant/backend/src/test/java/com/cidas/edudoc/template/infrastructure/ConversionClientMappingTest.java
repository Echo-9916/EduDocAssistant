package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.template.application.ConversionEngineException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 终审 I2 整改：超时映射对齐计划「引擎超时 → TIMEOUT/50401」而非误报不可达。
 * SocketTimeout（读超时先于引擎 504 返回是常态）单独映射 TIMEOUT；其余网络失败 ENGINE_UNAVAILABLE；
 * 上游结构化错误体取 code。
 */
class ConversionClientMappingTest {

    @Test
    void readTimeoutMapsToTimeoutNotUnreachable() {
        var e = ConversionClient.mapFailure(
                new ResourceAccessException("read timed out", new SocketTimeoutException()));
        assertEquals("TIMEOUT", e.getCode());
    }

    @Test
    void otherNetworkFailureMapsToEngineUnavailable() {
        var e = ConversionClient.mapFailure(
                new ResourceAccessException("refused", new IOException("connection refused")));
        assertEquals("ENGINE_UNAVAILABLE", e.getCode());
    }

    @Test
    void structuredUpstreamErrorKeepsCode() {
        RestClientResponseException response = new RestClientResponseException("422",
                HttpStatusCode.valueOf(422), "Unprocessable", new HttpHeaders(),
                "{\"code\":\"ENCRYPTED\",\"message\":\"PDF 已加密\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        var e = ConversionClient.mapFailure(response);
        assertEquals("ENCRYPTED", e.getCode());
        assertEquals("PDF 已加密", e.getMessage());
    }
}
