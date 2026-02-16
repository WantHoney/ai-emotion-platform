package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.repository.AudioRepository;
import com.wuhao.aiemotion.repository.ReportRepository;
import com.wuhao.aiemotion.repository.WarningGovernanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class WarningEventTriggerService {

    private static final Logger log = LoggerFactory.getLogger(WarningEventTriggerService.class);

    private final WarningGovernanceRepository warningGovernanceRepository;
    private final ReportRepository reportRepository;
    private final AudioRepository audioRepository;
    private final ObjectMapper objectMapper;

    public WarningEventTriggerService(WarningGovernanceRepository warningGovernanceRepository,
                                      ReportRepository reportRepository,
                                      AudioRepository audioRepository,
                                      ObjectMapper objectMapper) {
        this.warningGovernanceRepository = warningGovernanceRepository;
        this.reportRepository = reportRepository;
        this.audioRepository = audioRepository;
        this.objectMapper = objectMapper;
    }

    public void tryCreateWarningEvent(long taskId, long audioId, String reportJson, String overallEmotion, String riskLevelFromReport) {
        try {
            if (warningGovernanceRepository.findOpenWarningByTaskId(taskId).isPresent()) {
                return;
            }

            List<Map<String, Object>> rules = warningGovernanceRepository.listEnabledRules();
            if (rules.isEmpty()) {
                return;
            }

            double riskScore = resolveRiskScore(reportJson);
            Long reportId = reportRepository.findIdByTaskId(taskId).orElse(null);
            Long userId = audioRepository.findUserIdByAudioId(audioId).orElse(null);
            String normalizedEmotion = normalizeEmotion(overallEmotion);

            Map<String, Object> matchedRule = null;
            long trendHitCount = 0;
            for (Map<String, Object> rule : rules) {
                RuleEvalResult result = evaluateRule(rule, userId, normalizedEmotion, riskScore);
                if (result.matched()) {
                    matchedRule = rule;
                    trendHitCount = result.trendHitCount();
                    break;
                }
            }
            if (matchedRule == null) {
                return;
            }

            double mediumThreshold = toDouble(matchedRule.get("medium_threshold"), 60D);
            double highThreshold = toDouble(matchedRule.get("high_threshold"), 80D);
            String riskLevel = resolveRiskLevel(riskScore, riskLevelFromReport, mediumThreshold, highThreshold);

            String userMask = userId == null
                    ? "u_anonymous"
                    : "u_****" + String.valueOf(userId).substring(Math.max(0, String.valueOf(userId).length() - 3));

            LocalDateTime slaDeadline = LocalDateTime.now().plusMinutes(resolveSlaMinutes(matchedRule, riskLevel));

            String snapshot = objectMapper.writeValueAsString(Map.of(
                    "rule", Map.of(
                            "id", matchedRule.get("id"),
                            "ruleCode", matchedRule.get("rule_code"),
                            "ruleName", matchedRule.get("rule_name"),
                            "lowThreshold", matchedRule.get("low_threshold"),
                            "mediumThreshold", matchedRule.get("medium_threshold"),
                            "highThreshold", matchedRule.get("high_threshold"),
                            "trendWindowDays", matchedRule.get("trend_window_days"),
                            "triggerCount", matchedRule.get("trigger_count"),
                            "emotionCombo", matchedRule.get("emotion_combo_json")
                    ),
                    "runtime", Map.of(
                            "riskScore", riskScore,
                            "riskLevel", riskLevel,
                            "overallEmotion", normalizedEmotion,
                            "trendHitCount", trendHitCount,
                            "slaDeadlineAt", slaDeadline.toString()
                    )
            ));

            warningGovernanceRepository.createWarningEvent(
                    reportId,
                    taskId,
                    userId,
                    userMask,
                    riskScore,
                    riskLevel,
                    normalizedEmotion,
                    toLong(matchedRule.get("id")),
                    snapshot,
                    slaDeadline
            );
        } catch (Exception e) {
            log.warn("failed to trigger warning event, taskId={}, audioId={}", taskId, audioId, e);
        }
    }

    private RuleEvalResult evaluateRule(Map<String, Object> rule,
                                        Long userId,
                                        String overallEmotion,
                                        double riskScore) {
        double lowThreshold = toDouble(rule.get("low_threshold"), 40D);
        int trendWindowDays = Math.max(1, toInt(rule.get("trend_window_days"), 7));
        int triggerCount = Math.max(1, toInt(rule.get("trigger_count"), 1));

        if (riskScore < lowThreshold) {
            return RuleEvalResult.notMatched();
        }

        long trendHitCount;
        if (userId == null) {
            trendHitCount = 1;
        } else {
            trendHitCount = warningGovernanceRepository.countUserRiskReportsWithinWindow(userId, trendWindowDays, lowThreshold);
        }
        if (trendHitCount < triggerCount) {
            return RuleEvalResult.notMatched();
        }

        if (!matchEmotionCombo(rule.get("emotion_combo_json"), userId, trendWindowDays, overallEmotion)) {
            return RuleEvalResult.notMatched();
        }

        return new RuleEvalResult(true, trendHitCount);
    }

    private boolean matchEmotionCombo(Object comboValue,
                                      Long userId,
                                      int trendWindowDays,
                                      String currentEmotion) {
        JsonNode combo = toJsonNode(comboValue);
        if (combo == null || combo.isNull() || combo.isMissingNode()) {
            return true;
        }

        List<String> forbidden = readStringArray(combo.get("forbidden"));
        String normalizedCurrent = normalizeEmotion(currentEmotion);
        if (forbidden.stream().anyMatch(it -> it.equalsIgnoreCase(normalizedCurrent))) {
            return false;
        }

        JsonNode minHitsNode = combo.get("minHits");
        List<String> required = readStringArray(combo.get("required"));
        for (String emotion : required) {
            int requiredHits = 1;
            if (minHitsNode != null && minHitsNode.isObject() && minHitsNode.has(emotion) && minHitsNode.get(emotion).isInt()) {
                requiredHits = Math.max(1, minHitsNode.get(emotion).asInt());
            }

            long hits = normalizedCurrent.equalsIgnoreCase(emotion) ? 1 : 0;
            if (userId != null) {
                hits = warningGovernanceRepository.countUserEmotionWithinWindow(userId, trendWindowDays, emotion);
            }
            if (hits < requiredHits) {
                return false;
            }
        }

        return true;
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof String text) {
                if (text.isBlank()) {
                    return null;
                }
                return objectMapper.readTree(text);
            }
            return objectMapper.valueToTree(value);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> readStringArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().trim().toUpperCase(Locale.ROOT));
            }
        }
        return values;
    }

    private double resolveRiskScore(String reportJson) {
        if (reportJson == null || reportJson.isBlank()) {
            return 0D;
        }
        try {
            JsonNode root = objectMapper.readTree(reportJson);
            JsonNode scoreNode = root.at("/riskAssessment/risk_score");
            if (scoreNode.isNumber()) {
                double score = scoreNode.asDouble();
                return score <= 1D ? score * 100D : score;
            }
            JsonNode fallback = root.at("/analysis_result/risk_assessment/risk_score");
            if (fallback.isNumber()) {
                double score = fallback.asDouble();
                return score <= 1D ? score * 100D : score;
            }
            return 0D;
        } catch (Exception ignored) {
            return 0D;
        }
    }

    private String resolveRiskLevel(double riskScore, String reportRiskLevel, double medium, double high) {
        if (reportRiskLevel != null && !reportRiskLevel.isBlank()) {
            return reportRiskLevel.trim().toUpperCase(Locale.ROOT);
        }
        if (riskScore >= high) {
            return "HIGH";
        }
        if (riskScore >= medium) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private long resolveSlaMinutes(Map<String, Object> rule, String riskLevel) {
        return switch (riskLevel == null ? "LOW" : riskLevel.toUpperCase(Locale.ROOT)) {
            case "HIGH" -> Math.max(1, toInt(rule.get("sla_high_minutes"), 4 * 60));
            case "MEDIUM" -> Math.max(1, toInt(rule.get("sla_medium_minutes"), 12 * 60));
            default -> Math.max(1, toInt(rule.get("sla_low_minutes"), 24 * 60));
        };
    }

    private String normalizeEmotion(String emotion) {
        if (emotion == null || emotion.isBlank()) {
            return "UNKNOWN";
        }
        return emotion.trim().toUpperCase(Locale.ROOT);
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
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

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record RuleEvalResult(boolean matched, long trendHitCount) {
        static RuleEvalResult notMatched() {
            return new RuleEvalResult(false, 0);
        }
    }
}
