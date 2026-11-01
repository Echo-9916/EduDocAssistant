package com.cidas.edudoc.access.api;

import com.cidas.edudoc.access.application.AuthService;
import com.cidas.edudoc.common.api.ApiResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final AuthController controller = new AuthController(authService);

    @Test
    void resetPasswordReturnsPlaceholderMessage() {
        ApiResponse<Void> response = controller.resetPassword();

        assertEquals(200, response.code());
        assertEquals("该功能正在开发中", response.message());
        assertNull(response.data());
    }

    @Test
    void accessRequestReturnsPlaceholderMessage() {
        ApiResponse<Void> response = controller.accessRequest();

        assertEquals(200, response.code());
        assertEquals("该功能正在开发中", response.message());
        assertNull(response.data());
    }
}
