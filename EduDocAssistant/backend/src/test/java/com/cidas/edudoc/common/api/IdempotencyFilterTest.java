package com.cidas.edudoc.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdempotencyFilterTest {

    private final IdempotencyStore store = mock(IdempotencyStore.class);
    private final IdempotencyFilter filter = new IdempotencyFilter(store, new ObjectMapper());

    @Test
    void writeWithoutKeyRejects400() throws Exception {
        when(store.tryAcquire(any())).thenReturn(true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/documents");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(400, res.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void duplicateKeyRejects409() throws Exception {
        when(store.tryAcquire("k1")).thenReturn(false);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/documents");
        req.addHeader("Idempotency-Key", "k1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(409, res.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void newKeyPassesThrough() throws Exception {
        when(store.tryAcquire("k1")).thenReturn(true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/documents");
        req.addHeader("Idempotency-Key", "k1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        verify(chain).doFilter(req, res);
    }

    @Test
    void authWriteSkipsCheck() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        verifyNoInteractions(store);
    }
}
