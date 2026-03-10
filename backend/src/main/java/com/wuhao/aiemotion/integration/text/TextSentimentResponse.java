package com.wuhao.aiemotion.integration.text;

import java.util.Map;

public record TextSentimentResponse(
        String label,
        Double negativeScore,
        Map<String, Double> scores,
        Boolean emotion4Ready,
        Map<String, Double> emotion4Scores,
        String emotion4Label,
        Double emotion4Confidence,
        String topLabelRaw,
        Double topConfidenceRaw,
        Map<String, Double> rawScores,
        Meta meta
) {
    public record Meta(
            String engine,
            String model,
            String routeLanguage,
            String routingStrategy
    ) {
    }
}
