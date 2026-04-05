package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.config.AuthInterceptor;
import com.wuhao.aiemotion.service.AuthService;
import com.wuhao.aiemotion.service.ContentHubService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api")
public class ContentController {

    private final ContentHubService contentHubService;
    private final AuthService authService;

    public ContentController(ContentHubService contentHubService, AuthService authService) {
        this.contentHubService = contentHubService;
        this.authService = authService;
    }

    @GetMapping("/content-hub")
    public Map<String, Object> contentHub(@RequestParam(required = false) String date,
                                          @RequestParam(required = false) String category,
                                          HttpServletRequest request) {
        AuthService.UserProfile user = resolveOptionalUser(request);
        Long userId = user != null && AuthService.ROLE_USER.equals(user.role()) ? user.userId() : null;
        return contentHubService.hub(parseDate(date), category, userId);
    }

    @GetMapping("/content/articles/{id}")
    public Map<String, Object> articleDetail(@PathVariable long id) {
        return contentHubService.articleDetail(id);
    }

    @GetMapping("/content/books/{id}")
    public Map<String, Object> bookDetail(@PathVariable long id) {
        return contentHubService.bookDetail(id);
    }

    @PostMapping("/content/history")
    public void recordHistory(@RequestBody HistoryRequest request,
                              @RequestAttribute(AuthInterceptor.AUTH_USER_ATTR) AuthService.UserProfile user) {
        if (!AuthService.ROLE_USER.equals(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only user account can record content history");
        }
        contentHubService.recordHistory(user.userId(), request.action(), request.contentType(), request.contentId());
    }

    private AuthService.UserProfile resolveOptionalUser(HttpServletRequest request) {
        String accessToken = AuthInterceptor.resolveAccessToken(request);
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        try {
            return authService.requireValidUser(accessToken);
        } catch (ResponseStatusException ignore) {
            return null;
        }
    }

    private static LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date.trim());
    }

    public record HistoryRequest(
            @NotBlank String action,
            @NotBlank String contentType,
            @NotNull Long contentId
    ) {}
}
