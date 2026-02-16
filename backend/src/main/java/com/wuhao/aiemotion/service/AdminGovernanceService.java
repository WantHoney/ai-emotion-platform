package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.repository.ModelGovernanceRepository;
import com.wuhao.aiemotion.repository.WarningGovernanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminGovernanceService {

    private final ModelGovernanceRepository modelGovernanceRepository;
    private final WarningGovernanceRepository warningGovernanceRepository;
    private final ObjectMapper objectMapper;

    public AdminGovernanceService(ModelGovernanceRepository modelGovernanceRepository,
                                  WarningGovernanceRepository warningGovernanceRepository,
                                  ObjectMapper objectMapper) {
        this.modelGovernanceRepository = modelGovernanceRepository;
        this.warningGovernanceRepository = warningGovernanceRepository;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listModels(String modelType, String env, String status) {
        return modelGovernanceRepository.listModels(modelType, env, status);
    }

    public long createModel(String modelCode,
                            String modelName,
                            String modelType,
                            String provider,
                            String version,
                            String env,
                            String status,
                            Object metrics,
                            Object config,
                            Long operatorId) {
        return modelGovernanceRepository.createModel(
                normalizeCode(modelCode, "modelCode"),
                normalizeRequired(modelName, "modelName"),
                normalizeCode(modelType, "modelType").toUpperCase(),
                normalizeOptional(provider),
                normalizeRequired(version, "version"),
                normalizeEnv(env),
                normalizeStatus(status, "OFFLINE"),
                toJson(metrics),
                toJson(config),
                operatorId
        );
    }

    @Transactional
    public void switchModel(long modelId, String reason, Long operatorId) {
        Map<String, Object> target = modelGovernanceRepository.findModelById(modelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "model not found: " + modelId));
        String modelType = String.valueOf(target.get("model_type"));
        String env = String.valueOf(target.get("env"));
        Long fromModelId = modelGovernanceRepository.findOnlineModelId(modelType, env).orElse(null);

        modelGovernanceRepository.markOfflineByTypeEnv(modelType, env, modelId);
        int updated = modelGovernanceRepository.markOnline(modelId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "model switch failed");
        }
        modelGovernanceRepository.insertSwitchLog(modelType, env, fromModelId, modelId, normalizeOptional(reason), operatorId);
    }

    public List<Map<String, Object>> listModelSwitchLogs(String modelType, String env, Integer limit) {
        return modelGovernanceRepository.listSwitchLogs(modelType, env, limit == null ? 50 : limit);
    }

    public List<Map<String, Object>> listWarningRules() {
        return warningGovernanceRepository.listRules();
    }

    public long createWarningRule(String ruleCode,
                                  String ruleName,
                                  String description,
                                  boolean enabled,
                                  int priority,
                                  double lowThreshold,
                                  double mediumThreshold,
                                  double highThreshold,
                                  Object emotionCombo,
                                  int trendWindowDays,
                                  int triggerCount,
                                  String suggestTemplateCode,
                                  int slaLowMinutes,
                                  int slaMediumMinutes,
                                  int slaHighMinutes,
                                  Long operatorId) {
        validateThresholds(lowThreshold, mediumThreshold, highThreshold);
        return warningGovernanceRepository.createRule(
                normalizeCode(ruleCode, "ruleCode"),
                normalizeRequired(ruleName, "ruleName"),
                normalizeOptional(description),
                enabled,
                priority,
                lowThreshold,
                mediumThreshold,
                highThreshold,
                toJson(emotionCombo),
                Math.max(1, trendWindowDays),
                Math.max(1, triggerCount),
                normalizeOptional(suggestTemplateCode),
                normalizeSlaMinutes(slaLowMinutes, 24 * 60),
                normalizeSlaMinutes(slaMediumMinutes, 12 * 60),
                normalizeSlaMinutes(slaHighMinutes, 4 * 60),
                operatorId
        );
    }

    public void updateWarningRule(long id,
                                  String ruleName,
                                  String description,
                                  boolean enabled,
                                  int priority,
                                  double lowThreshold,
                                  double mediumThreshold,
                                  double highThreshold,
                                  Object emotionCombo,
                                  int trendWindowDays,
                                  int triggerCount,
                                  String suggestTemplateCode,
                                  int slaLowMinutes,
                                  int slaMediumMinutes,
                                  int slaHighMinutes) {
        validateThresholds(lowThreshold, mediumThreshold, highThreshold);
        int updated = warningGovernanceRepository.updateRule(
                id,
                normalizeRequired(ruleName, "ruleName"),
                normalizeOptional(description),
                enabled,
                priority,
                lowThreshold,
                mediumThreshold,
                highThreshold,
                toJson(emotionCombo),
                Math.max(1, trendWindowDays),
                Math.max(1, triggerCount),
                normalizeOptional(suggestTemplateCode),
                normalizeSlaMinutes(slaLowMinutes, 24 * 60),
                normalizeSlaMinutes(slaMediumMinutes, 12 * 60),
                normalizeSlaMinutes(slaHighMinutes, 4 * 60)
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "warning rule not found: " + id);
        }
    }

    public void toggleWarningRule(long id, boolean enabled) {
        int updated = warningGovernanceRepository.toggleRule(id, enabled);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "warning rule not found: " + id);
        }
    }

    public Map<String, Object> listWarnings(int page, int pageSize, String status, String riskLevel) {
        warningGovernanceRepository.markOverdueWarningsBreached();
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safePageSize;
        long total = warningGovernanceRepository.countWarnings(status, riskLevel);
        List<Map<String, Object>> items = warningGovernanceRepository.listWarnings(offset, safePageSize, status, riskLevel);
        return Map.of(
                "items", items,
                "total", total,
                "page", safePage,
                "pageSize", safePageSize
        );
    }

    public List<Map<String, Object>> listWarningActions(long warningId) {
        warningGovernanceRepository.findWarningById(warningId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "warning event not found: " + warningId));
        return warningGovernanceRepository.listWarningActions(warningId);
    }

    @Transactional
    public void handleWarning(long warningId,
                              String actionType,
                              String actionNote,
                              String templateCode,
                              String nextStatus,
                              Long operatorId) {
        warningGovernanceRepository.findWarningById(warningId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "warning event not found: " + warningId));

        String normalizedAction = normalizeCode(actionType, "actionType");
        String normalizedStatus = nextStatus == null || nextStatus.isBlank() ? null : normalizeCode(nextStatus, "nextStatus");

        warningGovernanceRepository.createWarningAction(
                warningId,
                normalizedAction,
                normalizeOptional(actionNote),
                normalizeOptional(templateCode),
                operatorId
        );
        warningGovernanceRepository.touchWarningWorkflow(warningId, normalizedAction, normalizedStatus);
        if (normalizedStatus != null) {
            warningGovernanceRepository.updateWarningStatus(warningId, normalizedStatus);
        }
        warningGovernanceRepository.markOverdueWarningsBreached();
    }

    public Map<String, Object> listAnalyticsDaily(Integer days) {
        int safeDays = Math.max(1, Math.min(days == null ? 14 : days, 30));
        List<Map<String, Object>> items = warningGovernanceRepository.listDailySummary(safeDays);
        if (items.isEmpty()) {
            items = warningGovernanceRepository.aggregateFallbackDaily(safeDays);
        }
        return Map.of(
                "items", items,
                "days", safeDays
        );
    }

    public Map<String, Object> analyticsQuality(Integer windowDays, Integer baselineDays) {
        warningGovernanceRepository.markOverdueWarningsBreached();

        int safeWindow = Math.max(1, Math.min(windowDays == null ? 7 : windowDays, 30));
        int safeBaseline = Math.max(1, Math.min(baselineDays == null ? 7 : baselineDays, 30));

        List<Map<String, Object>> currentRows = warningGovernanceRepository.listEmotionDistributionLastDays(safeWindow);
        List<Map<String, Object>> baselineRows = warningGovernanceRepository.listEmotionDistributionBeforeDays(safeWindow + safeBaseline, safeWindow);
        Map<String, Long> currentMap = toDistributionMap(currentRows);
        Map<String, Long> baselineMap = toDistributionMap(baselineRows);

        long currentTotal = currentMap.values().stream().mapToLong(Long::longValue).sum();
        long baselineTotal = baselineMap.values().stream().mapToLong(Long::longValue).sum();

        Set<String> emotions = new HashSet<>();
        emotions.addAll(currentMap.keySet());
        emotions.addAll(baselineMap.keySet());

        List<Map<String, Object>> driftItems = new ArrayList<>();
        for (String emotion : emotions) {
            long currentCount = currentMap.getOrDefault(emotion, 0L);
            long baselineCount = baselineMap.getOrDefault(emotion, 0L);
            double currentRatio = currentTotal == 0 ? 0D : (double) currentCount / currentTotal;
            double baselineRatio = baselineTotal == 0 ? 0D : (double) baselineCount / baselineTotal;
            driftItems.add(Map.of(
                    "emotion", emotion,
                    "currentCount", currentCount,
                    "baselineCount", baselineCount,
                    "currentRatio", currentRatio,
                    "baselineRatio", baselineRatio,
                    "drift", currentRatio - baselineRatio
            ));
        }
        driftItems.sort(Comparator.comparingDouble(item -> -Math.abs(toDouble(item.get("drift"), 0D))));

        List<Map<String, Object>> errorCategories = warningGovernanceRepository.listFailedTaskCategoryStats(safeWindow);
        List<Map<String, Object>> errorSamples = warningGovernanceRepository.listFailedTaskSamples(safeWindow, 20);
        Map<String, Object> slaOverview = warningGovernanceRepository.slaOverview(safeWindow);
        List<Map<String, Object>> slaTrend = warningGovernanceRepository.listSlaTrend(safeWindow);

        return Map.of(
                "windowDays", safeWindow,
                "baselineDays", safeBaseline,
                "emotionDrift", driftItems,
                "errorCategoryStats", errorCategories,
                "errorSamples", errorSamples,
                "slaOverview", slaOverview,
                "slaTrend", slaTrend
        );
    }

    public Map<String, Object> summary() {
        warningGovernanceRepository.markOverdueWarningsBreached();
        Map<String, Object> summary = new HashMap<>();
        List<Map<String, Object>> rules = warningGovernanceRepository.listRules();
        summary.put("ruleCount", rules.size());
        summary.put("enabledRuleCount", rules.stream().filter(it -> toInt(it.get("enabled"), 0) == 1).count());
        summary.put("warningCount", warningGovernanceRepository.countWarnings(null, null));
        return summary;
    }

    private void validateThresholds(double low, double medium, double high) {
        if (low > medium || medium > high) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "thresholds must satisfy low <= medium <= high");
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeCode(String value, String fieldName) {
        return normalizeRequired(value, fieldName).toUpperCase().replace('-', '_');
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeEnv(String value) {
        String env = normalizeOptional(value);
        return env == null ? "dev" : env.toLowerCase();
    }

    private String normalizeStatus(String value, String defaultValue) {
        String status = normalizeOptional(value);
        return status == null ? defaultValue : status.toUpperCase();
    }

    private int normalizeSlaMinutes(int value, int defaultValue) {
        if (value <= 0) {
            return defaultValue;
        }
        return Math.min(value, 14 * 24 * 60);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid json payload");
        }
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double toDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Map<String, Long> toDistributionMap(List<Map<String, Object>> rows) {
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String emotion = String.valueOf(row.getOrDefault("emotion", "UNKNOWN")).toUpperCase();
            long count = row.get("count") instanceof Number number ? number.longValue() : 0L;
            result.put(emotion, count);
        }
        return result;
    }
}
