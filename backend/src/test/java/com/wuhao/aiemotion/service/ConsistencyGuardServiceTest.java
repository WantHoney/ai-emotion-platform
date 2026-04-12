package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.dto.response.AnalysisTaskResultResponse;
import com.wuhao.aiemotion.integration.ser.SerAnalyzeResponse;
import com.wuhao.aiemotion.integration.text.TextSentimentResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsistencyGuardServiceTest {

    private final ConsistencyGuardService service =
            new ConsistencyGuardService(new AnalysisConsistencyProperties(), new ObjectMapper());

    @Test
    void shouldTriggerLowConsistencyWhenPositiveTextConflictsWithHighConfidenceSadVoice() {
        ConsistencyDecision decision = service.evaluate(
                37L,
                101L,
                "trace-37",
                "今天真的很开心，太阳非常好，晚上休息得很好。",
                sadBaseResponse(),
                textSentiment(false, 0.3224D),
                0.2646D
        );

        assertEquals(ConsistencyGuardService.LOW_CONSISTENCY_CODE, decision.code());
        assertEquals(ConsistencyGuardService.LOW_CONSISTENCY_STATUS, decision.status());
        assertEquals("SAD", decision.baseEmotionCode());
        assertEquals(0.9690D, decision.baseConfidence(), 0.0001D);
        assertTrue(decision.audit().triggered());
        assertTrue(decision.audit().positiveHits().contains("开心"));
        assertTrue(decision.audit().positiveHits().contains("太阳非常好"));
        assertTrue(decision.audit().positiveHits().contains("很好"));
        assertTrue(decision.audit().negativeHits().isEmpty());
        assertEquals(0.3224D, decision.audit().mappedMass(), 0.0001D);
        assertFalse(Boolean.TRUE.equals(decision.audit().emotion4Ready()));
        assertTrue(decision.audit().transcriptExcerpt().length() <= 120);
    }

    @Test
    void shouldAvoidPositiveHitInsideNegativeSpan() {
        ConsistencyDecision decision = service.evaluate(
                38L,
                102L,
                "trace-38",
                "我今天不开心，整个人都很难过。",
                sadBaseResponse(),
                textSentiment(false, 0.1200D),
                0.1200D
        );

        assertEquals("SAD", decision.code());
        assertEquals(ConsistencyGuardService.READY_STATUS, decision.status());
        assertFalse(decision.audit().triggered());
        assertTrue(decision.audit().negativeHits().contains("不开心"));
        assertTrue(decision.audit().negativeHits().contains("难过"));
        assertTrue(decision.audit().positiveHits().isEmpty());
    }

    @Test
    void shouldReturnExplicitFallbackDecisionWhenBaseEmotionMissing() {
        ConsistencyDecision decision = service.evaluate(
                39L,
                103L,
                "trace-39",
                "今天状态一般。",
                missingBaseResponse(),
                textSentiment(false, 0.0D),
                0.0D
        );

        assertEquals(ConsistencyGuardService.UNKNOWN_CODE, decision.code());
        assertEquals(ConsistencyGuardService.UNAVAILABLE_STATUS, decision.status());
        assertEquals(ConsistencyGuardService.BASE_EMOTION_MISSING_REASON, decision.reason());
        assertNull(decision.baseEmotionCode());
        assertNull(decision.baseConfidence());
        assertFalse(decision.audit().triggered());
    }

    @Test
    void shouldPrependReviewNoticeForLowConsistencyRiskAdvice() {
        AnalysisTaskResultResponse.RiskAssessmentPayload risk = new AnalysisTaskResultResponse.RiskAssessmentPayload(
                37.58D,
                "NORMAL",
                "保持当前节奏，继续进行日常放松与自我觉察。",
                1.0D,
                0.0D,
                0.0D,
                0.0D,
                0.2646D
        );

        AnalysisTaskResultResponse.RiskAssessmentPayload decorated = service.applyDecisionNotice(
                risk,
                new ConsistencyDecision(
                        ConsistencyGuardService.LOW_CONSISTENCY_CODE,
                        ConsistencyGuardService.LOW_CONSISTENCY_STATUS,
                        ConsistencyGuardService.SAD_POSITIVE_CONFLICT_REASON,
                        ConsistencyGuardService.CONSISTENCY_GUARD_SOURCE,
                        "SAD",
                        0.9690D,
                        null
                )
        );

        assertTrue(decorated.advice_text().startsWith(ConsistencyGuardService.LOW_CONSISTENCY_NOTICE));
    }

    private SerAnalyzeResponse sadBaseResponse() {
        return new SerAnalyzeResponse(
                new SerAnalyzeResponse.Overall("SAD", 0.9987D),
                List.of(),
                null,
                null,
                new SerAnalyzeResponse.Fusion(
                        true,
                        true,
                        "SAD",
                        "SAD",
                        0.9690D,
                        2.16D,
                        Map.of("SAD", 0.9690D),
                        Map.of("SAD", 0.9690D),
                        Map.of(),
                        null
                ),
                null
        );
    }

    private SerAnalyzeResponse missingBaseResponse() {
        return new SerAnalyzeResponse(
                null,
                List.of(),
                null,
                null,
                new SerAnalyzeResponse.Fusion(
                        true,
                        false,
                        null,
                        null,
                        null,
                        null,
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        null
                ),
                null
        );
    }

    private TextSentimentResponse textSentiment(boolean emotion4Ready, double mappedMass) {
        return new TextSentimentResponse(
                "negative",
                0.3529D,
                Map.of("negative", 0.3529D, "neutral", 0.3224D, "positive", 0.3247D),
                emotion4Ready,
                Map.of("ANG", 0.17645D, "HAP", 0.3247D, "NEU", 0.3224D, "SAD", 0.17645D),
                mappedMass,
                "NEU",
                0.3224D,
                "negative",
                0.3529D,
                Map.of("negative", 0.3529D, "neutral", 0.3224D, "positive", 0.3247D),
                new TextSentimentResponse.Meta("hf", "zh", "zh", "zh")
        );
    }
}
