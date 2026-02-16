package com.wuhao.aiemotion.config;

import com.wuhao.aiemotion.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String AUTH_USER_ATTR = "AUTH_USER";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            return true;
        }

        String token = resolveAccessToken(request);
        if (token == null || token.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "缺少 Authorization Bearer Token");
        }
        AuthService.UserProfile user = authService.requireValidUser(token);

        if (isAdminPath(path)) {
            authService.requireRole(user, Set.of(AuthService.ROLE_ADMIN));
        }

        request.setAttribute(AUTH_USER_ATTR, user);
        return true;
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/health")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/admin/login")
                || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/logout")
                || path.equals("/api/home")
                || path.startsWith("/api/psy-centers")
                || path.startsWith("/uploads/");
    }

    private boolean isAdminPath(String path) {
        return path.startsWith("/api/admin")
                || path.equals("/api/analysis/list")
                || path.equals("/api/admin/metrics")
                || path.equals("/api/system/status");
    }

    public static String resolveAccessToken(HttpServletRequest request) {
        String tokenFromHeader = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (tokenFromHeader != null && !tokenFromHeader.isBlank()) {
            return tokenFromHeader;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if ("accessToken".equals(cookie.getName()) || "access_token".equals(cookie.getName())) {
                String tokenFromCookie = extractBearerToken(cookie.getValue());
                if (tokenFromCookie != null && !tokenFromCookie.isBlank()) {
                    return tokenFromCookie;
                }
            }
        }
        return null;
    }

    private static String extractBearerToken(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String trimmed = rawValue.trim();
        if (trimmed.length() >= 7 && trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }
}
