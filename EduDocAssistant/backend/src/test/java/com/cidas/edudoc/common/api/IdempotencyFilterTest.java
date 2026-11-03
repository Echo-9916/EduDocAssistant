package com.cidas.edudoc.common.api;

import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdempotencyFilterTest {

    private final IdempotencyStore store = mock(IdempotencyStore.class);
    private final IdempotencyFilter filter = new IdempotencyFilter(store, new ObjectMapper());

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

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
        when(store.tryAcquire("-:POST:/api/v1/documents:k1")).thenReturn(false);
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
        when(store.tryAcquire("-:POST:/api/v1/documents:k1")).thenReturn(true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/documents");
        req.addHeader("Idempotency-Key", "k1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        verify(chain).doFilter(req, res);
    }

    @Test
    void keyScopedByUserMethodAndPath() throws Exception {
        AuthContext.set(new UserPrincipal("U1", "teacher", null, Set.of("TEACHER"), Set.of(), Set.of()));
        when(store.tryAcquire(any())).thenReturn(true);
        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/api/v1/documents/D1");
        req.addHeader("Idempotency-Key", "shared");

        filter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(store).tryAcquire(key.capture());
        // 1103 审查 S4：同 key 不同用户/方法/路径必须落在不同作用域
        assertEquals("U1:DELETE:/api/v1/documents/D1:shared", key.getValue());
        assertTrue(!key.getValue().contains("null"));
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
