package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.repository.AudioRepository;
import com.wuhao.aiemotion.repository.ReportRepository;
import com.wuhao.aiemotion.repository.WarningGovernanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

            Map<String, Object> rule = warningGovernanceRepository.findTopEnabledRule().orElse(null);
            if (rule == null) {
                return;
            }

            double lowThreshold = toDouble(rule.get("low_threshold"), 40D);
            double mediumThreshold = toDouble(rule.get("medium_threshold"), 60D);
            double highThreshold = toDouble(rule.get("high_threshold"), 80D);

            double riskScore = resolveRiskScore(reportJson);
            if (riskScore < lowThreshold) {
                return;
            }

            String riskLevel = resolveRiskLevel(riskScore, riskLevelFromReport, mediumThreshold, highThreshold);
            Long reportId = reportRepository.findIdByTaskId(taskId).orElse(null);
            Long userId = audioRepository.findUserIdByAudioId(audioId).orElse(null);
            String userMask = userId == null ? "u_anonymous" : "u_****" + String.valueOf(userId).substring(Math.max(0, String.valueOf(userId).length() - 3));

            String snapshot = objectMapper.writeValueAsString(Map.of(
                    "rule", Map.of(
                            "id", rule.get("id"),
                            "ruleCode", rule.get("rule_code"),
                            "ruleName", rule.get("rule_name"),
                            "lowThreshold", lowThreshold,
                            "mediumThreshold", mediumThreshold,
                            "highThreshold", highThreshold
                    ),
                    "runtime", Map.of(
                            "riskScore", riskScore,
                            "riskLevel", riskLevel,
                            "overallEmotion", overallEmotion
                    )
            ));

            warningGovernanceRepository.createWarningEvent(
                    reportId,
                    taskId,
                    userId,
                    userMask,
                    riskScore,
                    riskLevel,
                    overallEmotion,
                    toLong(rule.get("id")),
                    snapshot
            );
        } catch (Exception e) {
            log.warn("failed to trigger warning event, taskId={}, audioId={}", taskId, audioId, e);
        }
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
            return reportRiskLevel.trim().toUpperCase();
        }
        if (riskScore >= high) {
            return "HIGH";
        }
        if (riskScore >= medium) {
            return "MEDIUM";
        }
        return "LOW";
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
}
