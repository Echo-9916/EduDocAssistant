package com.cidas.edudoc.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void passesThroughHeaderAndClearsAfterChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TraceIdFilter.HEADER, "abc-123");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(req, resp, (r, s) -> seen.set(TraceContext.get()));

        assertEquals("abc-123", seen.get());
        assertEquals("abc-123", resp.getHeader(TraceIdFilter.HEADER));
        assertNull(TraceContext.get(), "过滤链结束后应清理 TraceContext");
    }

    @Test
    void generatesTraceIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, (r, s) -> {
        });

        String traceId = resp.getHeader(TraceIdFilter.HEADER);
        assertNotNull(traceId);
        assertEquals(32, traceId.length());
        assertNull(TraceContext.get());
    }
}
