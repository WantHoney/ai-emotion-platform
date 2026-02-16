package com.wuhao.aiemotion.dto;

import java.util.List;

public record EmotionAnalysisReport(
        String overallEmotion,
        Double confidence,
        List<KeyMoment> keyMoments,
        String summary
) {
    public record KeyMoment(
            Long startMs,
            Long endMs,
            String text,
            String emotion,
            Double score
    ) {}
}
