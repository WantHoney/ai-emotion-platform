package com.wuhao.aiemotion.service;

import java.util.List;

public record ConsistencyDecision(
        String code,
        String status,
        String reason,
        String source,
        String baseEmotionCode,
        Double baseConfidence,
        Audit audit
) {
    public boolean isLowConsistency() {
        return "LOW_CONSISTENCY".equalsIgnoreCase(code) || "LOW_CONSISTENCY".equalsIgnoreCase(status);
    }

    public record Audit(
            String rule,
            boolean triggered,
            Long taskId,
            Long audioId,
            String traceId,
            String baseEmotionCode,
            Double baseConfidence,
            String voiceLabel,
            Double voiceConfidence,
            String fusionLabel,
            Double fusionConfidence,
            Double fusedTextNeg,
            String textModelLabel,
            Double textModelNegative,
            Double mappedMass,
            Boolean emotion4Ready,
            List<String> positiveHits,
            List<String> negativeHits,
            String transcriptExcerpt,
            Thresholds thresholds
    ) {
    }

    public record Thresholds(
            Double voiceConfidenceMin,
            Double fusedTextNegMax
    ) {
    }
}
