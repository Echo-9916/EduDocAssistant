package com.cidas.edudoc.common.security;

import com.cidas.edudoc.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RestSecurityHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void noTokenReturns401WithCode40101() throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response, mock(AuthenticationException.class));

        assertEquals(401, response.getStatus());
        assertEquals(40101, objectMapper.readValue(response.getContentAsString(), ApiResponse.class).code());
    }

    @Test
    void noPermissionReturns403WithCode40301() throws Exception {
        RestAccessDeniedHandler handler = new RestAccessDeniedHandler(objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

        assertEquals(403, response.getStatus());
        assertEquals(40301, objectMapper.readValue(response.getContentAsString(), ApiResponse.class).code());
    }
}
