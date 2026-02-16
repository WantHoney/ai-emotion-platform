package com.wuhao.aiemotion.domain;

import java.time.LocalDateTime;

public record AnalysisSegment(
        long id,
        long taskId,
        int startMs,
        int endMs,
        String emotionCode,
        double confidence,
        LocalDateTime createdAt
) {
}
