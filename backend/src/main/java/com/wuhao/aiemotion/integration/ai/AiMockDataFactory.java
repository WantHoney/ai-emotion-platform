package com.wuhao.aiemotion.integration.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wuhao.aiemotion.integration.ai.AiAnalysisResult.AiEmotionScore;
import com.wuhao.aiemotion.integration.ai.AiAnalysisResult.AiEmotionSummary;
import com.wuhao.aiemotion.integration.ai.AiAnalysisResult.AiSegment;
import org.springframework.stereotype.Component;

@Component
public class AiMockDataFactory {

    public AiAnalysisResult buildBaseResult(AiAnalysisRequest request) {
        List<AiSegment> segments = List.of(
                new AiSegment(
                        0,
                        8000,
                        "（片段1）今天心情不错，感觉很放松。",
                        List.of(
                                new AiEmotionScore("HAPPY", "开心", "A", 0.82),
                                new AiEmotionScore("CALM", "平静", "A", 0.63)
                        )
                ),
                new AiSegment(
                        8000,
                        16000,
                        "（片段2）但有些事情让我有点烦。",
                        List.of(
                                new AiEmotionScore("ANGRY", "生气", "A", 0.74),
                                new AiEmotionScore("SAD", "难过", "A", 0.41)
                        )
                ),
                new AiSegment(
                        16000,
                        24000,
                        "（片段3）总体来说还能接受。",
                        List.of(
                                new AiEmotionScore("CALM", "平静", "A", 0.77),
                                new AiEmotionScore("HAPPY", "开心", "A", 0.36)
                        )
                )
        );

        List<AiEmotionSummary> overallEmotions = calculateOverall(segments);

        Map<String, Object> summaryJson = new HashMap<>();
        if (!overallEmotions.isEmpty()) {
            AiEmotionSummary top = overallEmotions.get(0);
            summaryJson.put("overallEmotion", top.code());
            summaryJson.put("confidence", top.score());
            summaryJson.put("note", "mock result (no AI yet)");
        }

        return new AiAnalysisResult(summaryJson, segments, overallEmotions, null);
    }

    private List<AiEmotionSummary> calculateOverall(List<AiSegment> segments) {
        Map<String, Double> scoreSum = new HashMap<>();
        Map<String, String> nameZh = new HashMap<>();
        for (AiSegment segment : segments) {
            for (AiEmotionScore emotion : segment.emotions()) {
                scoreSum.merge(emotion.code(), emotion.score(), Double::sum);
                nameZh.putIfAbsent(emotion.code(), emotion.nameZh());
            }
        }

        if (scoreSum.isEmpty()) {
            return List.of();
        }

        double total = scoreSum.values().stream().mapToDouble(Double::doubleValue).sum();
        List<AiEmotionSummary> results = new ArrayList<>();
        for (var entry : scoreSum.entrySet()) {
            double normalized = total == 0 ? 0.0 : entry.getValue() / total;
            results.add(new AiEmotionSummary(entry.getKey(), nameZh.get(entry.getKey()), normalized));
        }
        results.sort((a, b) -> Double.compare(b.score(), a.score()));
        return results;
    }
}
