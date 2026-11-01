package com.cidas.edudoc.access.api;

import com.cidas.edudoc.access.application.AuthService;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthService.LoginResult> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username(), request.password()));
    }

    @GetMapping("/me")
    public ApiResponse<UserPrincipal> me() {
        return ApiResponse.ok(authService.me());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String authorization) {
        authService.logout(authorization.substring(7));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword() {
        return ApiResponse.message("该功能正在开发中");
    }

    @PostMapping("/access-request")
    public ApiResponse<Void> accessRequest() {
        return ApiResponse.message("该功能正在开发中");
    }

    public record LoginRequest(String username, String password) {
    }
}
