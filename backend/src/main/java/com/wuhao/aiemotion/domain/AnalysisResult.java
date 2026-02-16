package com.wuhao.aiemotion.domain;

import java.time.LocalDateTime;

public record AnalysisResult(
        long id,
        long taskId,
        String modelName,
        String overallEmotionCode,
        Double overallConfidence,
        Integer durationMs,
        Integer sampleRate,
        String rawJson,
        LocalDateTime createdAt
) {
}
