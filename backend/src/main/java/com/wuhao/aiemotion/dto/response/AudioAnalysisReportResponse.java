package com.wuhao.aiemotion.dto.response;

import java.util.List;

public record AudioAnalysisReportResponse(
        long analysisId,
        long audioId,
        String modelName,
        String modelVersion,
        String status,
        Object summary,
        String errorMessage,
        String createdAt,
        String updatedAt,
        Overall overall,              // ✅ 新增：聚合结果
        List<Segment> segments
) {
    public record Overall(
            String emotionCode,
            String emotionNameZh,
            double confidence
    ) {}

    public record Segment(
            long segmentId,
            long startMs,
            long endMs,
            String transcript,
            List<Emotion> emotions
    ) {}

    public record Emotion(
            long emotionId,
            String code,
            String nameZh,
            String scheme,
            double score
    ) {}
}
