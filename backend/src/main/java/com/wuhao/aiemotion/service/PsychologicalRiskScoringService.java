package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.domain.AnalysisSegment;
import com.wuhao.aiemotion.dto.response.AnalysisTaskResultResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class PsychologicalRiskScoringService {

    public static final String PSI_VERSION = "psi_heuristic_v2_light_tuned";

    private static final double WEIGHT_SAD = 0.45;
    private static final double WEIGHT_ANGRY = 0.22;
    private static final double WEIGHT_HAPPY_OFFSET = 0.28;
    private static final double WEIGHT_NEUTRAL_OFFSET = 0.08;
    private static final double WEIGHT_VAR_CONF = 0.08;
    private static final double WEIGHT_VOICE_IN_PSI = 0.65;
    private static final double WEIGHT_TEXT_IN_PSI = 0.35;
    private static final double TEXT_NEG_CONFLICT_DISCOUNT = 0.75;
    private static final double TEXT_NEG_CONFLICT_MIN_HAPPY = 0.35;
    private static final double TEXT_NEG_CONFLICT_MIN_TEXT_NEG = 0.50;
    private static final double TEXT_NEG_CONFLICT_MAX_SAD = 0.35;
    public static final double ATTENTION_THRESHOLD_SCORE = 40.0D;
    public static final double HIGH_RISK_THRESHOLD_SCORE = 70.0D;

    private final InterventionAdviceService interventionAdviceService;

    public PsychologicalRiskScoringService(InterventionAdviceService interventionAdviceService) {
        this.interventionAdviceService = interventionAdviceService;
    }

    public AnalysisTaskResultResponse.RiskAssessmentPayload evaluate(List<AnalysisSegment> segments) {
        return evaluate(segments, null, null, null, 0.0D);
    }

    public AnalysisTaskResultResponse.RiskAssessmentPayload evaluate(List<AnalysisSegment> segments, double textNeg) {
        return evaluate(segments, null, null, null, textNeg);
    }

    public AnalysisTaskResultResponse.RiskAssessmentPayload evaluate(List<AnalysisSegment> segments,
                                                                     Double pSadOverride,
                                                                     Double pAngryOverride,
                                                                     Double pHappyOverride,
                                                                     double textNeg) {
        double pSad = probabilityOrFallback(pSadOverride, segments, "sad");
        double pAngry = probabilityOrFallback(pAngryOverride, segments, "angry");
        double pHappy = probabilityOrFallback(pHappyOverride, segments, "happy");
        double pNeutral = clamp(1.0D - pSad - pAngry - pHappy, 0.0D, 1.0D);
        double varConf = confidenceVariance(segments);
        double normalizedTextNeg = clamp(textNeg, 0.0D, 1.0D);
        double adjustedTextNeg = applyTextNegCalibration(normalizedTextNeg, pSad, pHappy);

        double voiceRisk = 100.0D * clamp(
                WEIGHT_SAD * pSad
                        + WEIGHT_ANGRY * pAngry
                        + WEIGHT_VAR_CONF * varConf
                        - WEIGHT_HAPPY_OFFSET * pHappy
                        - WEIGHT_NEUTRAL_OFFSET * pNeutral,
                0.0D,
                1.0D
        );
        double textRisk = 100.0D * adjustedTextNeg;
        double riskScore = clamp(
                WEIGHT_VOICE_IN_PSI * voiceRisk + WEIGHT_TEXT_IN_PSI * textRisk,
                0.0D,
                100.0D
        );
        String riskLevel = toRiskLevel(riskScore);
        String adviceText = interventionAdviceService.buildAdvice(
                riskLevel,
                riskScore,
                pSad,
                pAngry,
                varConf,
                adjustedTextNeg
        );

        return new AnalysisTaskResultResponse.RiskAssessmentPayload(
                round2(riskScore),
                riskLevel,
                adviceText,
                round4(pSad),
                round4(pAngry),
                round4(pHappy),
                round4(varConf),
                round4(adjustedTextNeg)
        );
    }

    private double applyTextNegCalibration(double textNeg, double pSad, double pHappy) {
        if (pHappy >= TEXT_NEG_CONFLICT_MIN_HAPPY
                && textNeg >= TEXT_NEG_CONFLICT_MIN_TEXT_NEG
                && pSad <= TEXT_NEG_CONFLICT_MAX_SAD) {
            return clamp(textNeg * TEXT_NEG_CONFLICT_DISCOUNT, 0.0D, 1.0D);
        }
        return textNeg;
    }

    private double probabilityOrFallback(Double overrideValue, List<AnalysisSegment> segments, String emotionCode) {
        if (overrideValue != null && Double.isFinite(overrideValue)) {
            return clamp(overrideValue, 0.0D, 1.0D);
        }
        return durationRatioByEmotion(segments, emotionCode);
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
        if (riskScore >= HIGH_RISK_THRESHOLD_SCORE) {
            return "HIGH";
        }
        if (riskScore >= ATTENTION_THRESHOLD_SCORE) {
            return "ATTENTION";
        }
        return "NORMAL";
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
