package com.wuhao.aiemotion.integration.ai;

import java.util.List;

public record AiAnalysisResult(
        Object summaryJson,
        List<AiSegment> segments,
        List<AiEmotionSummary> overallEmotions,
        Object reportJson
) {
    public AiAnalysisResult withReportJson(Object reportJson) {
        return new AiAnalysisResult(summaryJson, segments, overallEmotions, reportJson);
    }

    public record AiSegment(
            long startMs,
            long endMs,
            String text,
            List<AiEmotionScore> emotions
    ) {}

    public record AiEmotionScore(
            String code,
            String nameZh,
            String scheme,
            double score
    ) {}

    public record AiEmotionSummary(
            String code,
            String nameZh,
            double score
    ) {}
}
