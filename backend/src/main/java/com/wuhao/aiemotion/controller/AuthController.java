package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.config.AuthInterceptor;
import com.wuhao.aiemotion.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody @Valid LoginRequest request) {
        AuthService.AuthTokens tokens = authService.register(request.username(), request.password());
        return AuthResponse.from(tokens);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody @Valid LoginRequest request) {
        AuthService.AuthTokens tokens = authService.login(request.username(), request.password(), null);
        return AuthResponse.from(tokens);
    }

    @PostMapping("/admin/login")
    public AuthResponse adminLogin(@RequestBody @Valid LoginRequest request) {
        AuthService.AuthTokens tokens = authService.login(request.username(), request.password(), AuthService.ROLE_ADMIN);
        return AuthResponse.from(tokens);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody @Valid RefreshRequest request) {
        AuthService.AuthTokens tokens = authService.refresh(request.refreshToken());
        return AuthResponse.from(tokens);
    }

    @GetMapping("/me")
    public MeResponse me(HttpServletRequest request) {
        AuthService.UserProfile user = (AuthService.UserProfile) request.getAttribute(AuthInterceptor.AUTH_USER_ATTR);
        return new MeResponse(user.userId(), user.username(), user.role());
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        String token = AuthInterceptor.resolveAccessToken(request);
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "缺少 Authorization Bearer Token");
        }
        authService.logout(token);
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record AuthResponse(String accessToken, String refreshToken, long accessExpiresIn, long refreshExpiresIn, MeResponse user) {
        public static AuthResponse from(AuthService.AuthTokens tokens) {
            AuthService.UserProfile user = tokens.user();
            return new AuthResponse(
                    tokens.accessToken(),
                    tokens.refreshToken(),
                    tokens.accessExpiresIn(),
                    tokens.refreshExpiresIn(),
                    new MeResponse(user.userId(), user.username(), user.role())
            );
        }
    }

    public record MeResponse(long userId, String username, String role) {
    }
}
