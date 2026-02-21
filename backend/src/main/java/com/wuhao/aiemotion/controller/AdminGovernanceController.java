package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.config.AuthInterceptor;
import com.wuhao.aiemotion.service.AdminGovernanceService;
import com.wuhao.aiemotion.service.AuthService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminGovernanceController {

    private final AdminGovernanceService adminGovernanceService;

    public AdminGovernanceController(AdminGovernanceService adminGovernanceService) {
        this.adminGovernanceService = adminGovernanceService;
    }

    @GetMapping("/models")
    public List<Map<String, Object>> models(@RequestParam(required = false) String modelType,
                                            @RequestParam(required = false) String env,
                                            @RequestParam(required = false) String status) {
        return adminGovernanceService.listModels(modelType, env, status);
    }

    @PostMapping("/models")
    public Map<String, Object> createModel(@RequestBody CreateModelRequest request,
                                           @RequestAttribute(AuthInterceptor.AUTH_USER_ATTR) AuthService.UserProfile user) {
        long modelId = adminGovernanceService.createModel(
                request.modelCode(),
                request.modelName(),
                request.modelType(),
                request.provider(),
                request.version(),
                request.env(),
                request.status(),
                request.metrics(),
                request.config(),
                user.userId()
        );
        return Map.of("id", modelId);
    }

    @PostMapping("/models/{modelId}/switch")
    public Map<String, Object> switchModel(@PathVariable long modelId,
                                           @RequestBody(required = false) SwitchModelRequest request,
                                           @RequestAttribute(AuthInterceptor.AUTH_USER_ATTR) AuthService.UserProfile user) {
        adminGovernanceService.switchModel(modelId, request == null ? null : request.reason(), user.userId());
        return Map.of("success", true);
    }

    @GetMapping("/models/switch-logs")
    public List<Map<String, Object>> switchLogs(@RequestParam(required = false) String modelType,
                                                @RequestParam(required = false) String env,
                                                @RequestParam(required = false) Integer limit) {
        return adminGovernanceService.listModelSwitchLogs(modelType, env, limit);
    }

    @GetMapping("/warning-rules")
    public List<Map<String, Object>> warningRules() {
        return adminGovernanceService.listWarningRules();
    }

    @PostMapping("/warning-rules")
    public Map<String, Object> createWarningRule(@RequestBody CreateWarningRuleRequest request,
                                                 @RequestAttribute(AuthInterceptor.AUTH_USER_ATTR) AuthService.UserProfile user) {
        long id = adminGovernanceService.createWarningRule(
                request.ruleCode(),
                request.ruleName(),
                request.description(),
                request.enabled(),
                request.priority(),
                request.lowThreshold(),
                request.mediumThreshold(),
                request.highThreshold(),
                request.emotionCombo(),
                request.trendWindowDays(),
                request.triggerCount(),
                request.suggestTemplateCode(),
                request.slaLowMinutesOrDefault(),
                request.slaMediumMinutesOrDefault(),
                request.slaHighMinutesOrDefault(),
                user.userId()
        );
        return Map.of("id", id);
    }

    @PutMapping("/warning-rules/{ruleId}")
    public Map<String, Object> updateWarningRule(@PathVariable long ruleId,
                                                 @RequestBody UpdateWarningRuleRequest request) {
        adminGovernanceService.updateWarningRule(
                ruleId,
                request.ruleName(),
                request.description(),
                request.enabled(),
                request.priority(),
                request.lowThreshold(),
                request.mediumThreshold(),
                request.highThreshold(),
                request.emotionCombo(),
                request.trendWindowDays(),
                request.triggerCount(),
                request.suggestTemplateCode(),
                request.slaLowMinutesOrDefault(),
                request.slaMediumMinutesOrDefault(),
                request.slaHighMinutesOrDefault()
        );
        return Map.of("success", true);
    }

    @PostMapping("/warning-rules/{ruleId}/toggle")
    public Map<String, Object> toggleWarningRule(@PathVariable long ruleId,
                                                 @RequestParam boolean enabled) {
        adminGovernanceService.toggleWarningRule(ruleId, enabled);
        return Map.of("success", true);
    }

    @GetMapping("/warnings")
    public Map<String, Object> warnings(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String riskLevel) {
        return adminGovernanceService.listWarnings(page, pageSize, status, riskLevel);
    }

    @PostMapping("/warnings/{warningId}/actions")
    public Map<String, Object> handleWarning(@PathVariable long warningId,
                                             @RequestBody WarningActionRequest request,
                                             @RequestAttribute(AuthInterceptor.AUTH_USER_ATTR) AuthService.UserProfile user) {
        adminGovernanceService.handleWarning(
                warningId,
                request.actionType(),
                request.actionNote(),
                request.templateCode(),
                request.nextStatus(),
                user.userId()
        );
        return Map.of("success", true);
    }

    @GetMapping("/warnings/{warningId}/actions")
    public List<Map<String, Object>> warningActions(@PathVariable long warningId) {
        return adminGovernanceService.listWarningActions(warningId);
    }

    @GetMapping("/analytics/daily")
    public Map<String, Object> analyticsDaily(@RequestParam(required = false) Integer days) {
        return adminGovernanceService.listAnalyticsDaily(days);
    }

    @GetMapping("/analytics/quality")
    public Map<String, Object> analyticsQuality(@RequestParam(required = false) Integer windowDays,
                                                @RequestParam(required = false) Integer baselineDays) {
        return adminGovernanceService.analyticsQuality(windowDays, baselineDays);
    }

    @GetMapping("/governance/summary")
    public Map<String, Object> summary() {
        return adminGovernanceService.summary();
    }

    @PostMapping("/governance/drift/scan")
    public Map<String, Object> triggerDriftScan(@RequestParam(required = false) Integer windowDays,
                                                @RequestParam(required = false) Integer baselineDays,
                                                @RequestParam(required = false) Double mediumThreshold,
                                                @RequestParam(required = false) Double highThreshold,
                                                @RequestParam(required = false) Integer minSamples) {
        int created = adminGovernanceService.triggerModelDriftWarnings(
                windowDays == null ? 7 : windowDays,
                baselineDays == null ? 7 : baselineDays,
                mediumThreshold == null ? 0.15D : mediumThreshold,
                highThreshold == null ? 0.25D : highThreshold,
                minSamples == null ? 20 : minSamples,
                "api_manual"
        );
        return Map.of("created", created);
    }

    public record CreateModelRequest(
            @NotBlank String modelCode,
            @NotBlank String modelName,
            @NotBlank String modelType,
            String provider,
            @NotBlank String version,
            String env,
            String status,
            Object metrics,
            Object config
    ) {
    }

    public record SwitchModelRequest(String reason) {
    }

    public record CreateWarningRuleRequest(
            @NotBlank String ruleCode,
            @NotBlank String ruleName,
            String description,
            boolean enabled,
            @NotNull Integer priority,
            @NotNull Double lowThreshold,
            @NotNull Double mediumThreshold,
            @NotNull Double highThreshold,
            Object emotionCombo,
            @NotNull Integer trendWindowDays,
            @NotNull Integer triggerCount,
            String suggestTemplateCode,
            Integer slaLowMinutes,
            Integer slaMediumMinutes,
            Integer slaHighMinutes
    ) {
        public int slaLowMinutesOrDefault() {
            return slaLowMinutes == null ? 24 * 60 : slaLowMinutes;
        }

        public int slaMediumMinutesOrDefault() {
            return slaMediumMinutes == null ? 12 * 60 : slaMediumMinutes;
        }

        public int slaHighMinutesOrDefault() {
            return slaHighMinutes == null ? 4 * 60 : slaHighMinutes;
        }
    }

    public record UpdateWarningRuleRequest(
            @NotBlank String ruleName,
            String description,
            boolean enabled,
            @NotNull Integer priority,
            @NotNull Double lowThreshold,
            @NotNull Double mediumThreshold,
            @NotNull Double highThreshold,
            Object emotionCombo,
            @NotNull Integer trendWindowDays,
            @NotNull Integer triggerCount,
            String suggestTemplateCode,
            Integer slaLowMinutes,
            Integer slaMediumMinutes,
            Integer slaHighMinutes
    ) {
        public int slaLowMinutesOrDefault() {
            return slaLowMinutes == null ? 24 * 60 : slaLowMinutes;
        }

        public int slaMediumMinutesOrDefault() {
            return slaMediumMinutes == null ? 12 * 60 : slaMediumMinutes;
        }

        public int slaHighMinutesOrDefault() {
            return slaHighMinutes == null ? 4 * 60 : slaHighMinutes;
        }
    }

    public record WarningActionRequest(
            @NotBlank String actionType,
            String actionNote,
            String templateCode,
            String nextStatus
    ) {
    }
}
