package com.wuhao.aiemotion.integration.ser;

import java.util.List;

public record SerAnalyzeResponse(
        Overall overall,
        List<Segment> segments,
        AudioSummary audioSummary,
        TextFeatures textFeatures,
        Fusion fusion,
        Meta meta
) {
    public record Overall(
            String emotionCode,
            double confidence
    ) {}

    public record Segment(
            long startMs,
            long endMs,
            String emotionCode,
            double confidence
    ) {}

    public record AudioSummary(
            Double audio_prob_ang,
            Double audio_prob_hap,
            Double audio_prob_neu,
            Double audio_prob_sad,
            Double audio_confidence,
            Double audio_entropy,
            String dominantEmotion
    ) {}

    public record TextFeatures(
            Double text_negative,
            Double text_neutral,
            Double text_positive,
            Double text_negative_score,
            Double text_length_norm
    ) {}

    public record Fusion(
            Boolean enabled,
            Boolean ready,
            String labelRaw,
            String label,
            Double confidence,
            Double temperature,
            java.util.Map<String, Double> scoresRaw,
            java.util.Map<String, Double> scores,
            java.util.Map<String, Double> features,
            String error
    ) {
    }

    public record Meta(
            String model,
            String engine,
            String routeLanguage,
            String routingStrategy,
            String languageHint,
            int sampleRate,
            long durationMs,
            Boolean fusionEnabled
    ) {}
}
