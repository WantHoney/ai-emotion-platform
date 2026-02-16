package com.wuhao.aiemotion.integration.ser;

import java.util.List;

public record SerAnalyzeResponse(
        Overall overall,
        List<Segment> segments,
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

    public record Meta(
            String model,
            int sampleRate,
            long durationMs
    ) {}
}
