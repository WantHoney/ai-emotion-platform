package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.config.AuthInterceptor;
import com.wuhao.aiemotion.service.AdminInboxCenterService;
import com.wuhao.aiemotion.service.AuthService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminInboxController {

    private final AdminInboxCenterService adminInboxCenterService;

    public AdminInboxController(AdminInboxCenterService adminInboxCenterService) {
        this.adminInboxCenterService = adminInboxCenterService;
    }

    @GetMapping("/inbox")
    public Map<String, Object> inbox(@RequestParam(required = false) Integer limit,
                                     @RequestParam(defaultValue = "false") boolean archived,
                                     @RequestParam(defaultValue = "false") boolean pendingWarningsOnly,
                                     @RequestAttribute(AuthInterceptor.AUTH_USER_ATTR) AuthService.UserProfile user) {
        return adminInboxCenterService.listInbox(user.userId(), limit, archived, pendingWarningsOnly);
    }

    @PutMapping("/inbox/preferences")
    public Map<String, Object> updatePreferences(@RequestBody UpdateInboxPreferenceRequest request,
                                                 @RequestAttribute(AuthInterceptor.AUTH_USER_ATTR) AuthService.UserProfile user) {
        return adminInboxCenterService.updatePreferences(
                user.userId(),
                request.warningEnabled(),
                request.systemEnabled(),
                request.modelEnabled(),
                request.scheduleEnabled(),
                request.psyCenterEnabled()
        );
    }

    @PostMapping("/inbox/state")
    public Map<String, Object> updateState(@RequestBody UpdateInboxStateRequest request,
                                           @RequestAttribute(AuthInterceptor.AUTH_USER_ATTR) AuthService.UserProfile user) {
        adminInboxCenterService.updateState(
                user.userId(),
                request.items().stream()
                        .map(item -> new AdminInboxCenterService.InboxStateInput(item.messageId(), item.sourceType(), item.sourceId()))
                        .toList(),
                request.read(),
                request.archived()
        );
        return Map.of("success", true);
    }

    public record UpdateInboxPreferenceRequest(
            Boolean warningEnabled,
            Boolean systemEnabled,
            Boolean modelEnabled,
            Boolean scheduleEnabled,
            Boolean psyCenterEnabled
    ) {
    }

    public record UpdateInboxStateRequest(
            @NotEmpty List<InboxStateItemRequest> items,
            Boolean read,
            Boolean archived
    ) {
    }

    public record InboxStateItemRequest(
            @NotBlank String messageId,
            String sourceType,
            String sourceId
    ) {
    }
}
