package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.domain.AnalysisSegment;
import com.wuhao.aiemotion.dto.response.AnalysisTaskResultResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class PsychologicalRiskScoringService {

    private static final double WEIGHT_SAD = 0.45;
    private static final double WEIGHT_ANGRY = 0.25;
    private static final double WEIGHT_VAR_CONF = 0.10;

    public AnalysisTaskResultResponse.RiskAssessmentPayload evaluate(List<AnalysisSegment> segments) {
        return evaluate(segments, 0.0D);
    }

    public AnalysisTaskResultResponse.RiskAssessmentPayload evaluate(List<AnalysisSegment> segments, double textNeg) {
        double pSad = durationRatioByEmotion(segments, "sad");
        double pAngry = durationRatioByEmotion(segments, "angry");
        double varConf = confidenceVariance(segments);
        double normalizedTextNeg = clamp(textNeg, 0.0D, 1.0D);

        double voiceRisk = 100.0D * (
                WEIGHT_SAD * pSad
                        + WEIGHT_ANGRY * pAngry
                        + WEIGHT_VAR_CONF * varConf
        );
        double textRisk = 100.0D * normalizedTextNeg;
        double riskScore = clamp(0.6D * voiceRisk + 0.4D * textRisk, 0.0D, 100.0D);
        String riskLevel = toRiskLevel(riskScore);

        return new AnalysisTaskResultResponse.RiskAssessmentPayload(
                round2(riskScore),
                riskLevel,
                adviceText(riskLevel),
                round4(pSad),
                round4(pAngry),
                round4(varConf),
                round4(normalizedTextNeg)
        );
    }

    private double durationRatioByEmotion(List<AnalysisSegment> segments, String emotionCode) {
        if (segments == null || segments.isEmpty()) {
            return 0.0D;
        }

        long totalDuration = 0L;
        long matchedDuration = 0L;

        for (AnalysisSegment segment : segments) {
            int duration = Math.max(0, segment.endMs() - segment.startMs());
            totalDuration += duration;
            if (emotionCode.equals(normalizeEmotion(segment.emotionCode()))) {
                matchedDuration += duration;
            }
        }

        if (totalDuration <= 0L) {
            return 0.0D;
        }
        return (double) matchedDuration / (double) totalDuration;
    }

    private double confidenceVariance(List<AnalysisSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return 0.0D;
        }

        double mean = segments.stream().mapToDouble(AnalysisSegment::confidence).average().orElse(0.0D);
        double variance = segments.stream()
                .mapToDouble(segment -> {
                    double diff = segment.confidence() - mean;
                    return diff * diff;
                })
                .average()
                .orElse(0.0D);

        return clamp(variance, 0.0D, 1.0D);
    }

    private String normalizeEmotion(String emotionCode) {
        return emotionCode == null ? "" : emotionCode.trim().toLowerCase(Locale.ROOT);
    }

    private String toRiskLevel(double riskScore) {
        if (riskScore >= 70.0D) {
            return "HIGH";
        }
        if (riskScore >= 40.0D) {
            return "ATTENTION";
        }
        return "NORMAL";
    }

    private String adviceText(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> "当前情绪风险较高。建议优先减少高压情境，尽快与可信赖的人沟通；如持续不适，建议咨询专业人士或联系心理热线寻求支持。";
            case "ATTENTION" -> "当前情绪风险需要关注。建议安排休息、与朋友沟通、减少压力源；如状态持续波动，可考虑寻求进一步帮助。";
            default -> "当前情绪风险总体平稳。建议保持规律睡眠、适度运动，并持续记录情绪变化。";
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0D) / 10000.0D;
    }
}
