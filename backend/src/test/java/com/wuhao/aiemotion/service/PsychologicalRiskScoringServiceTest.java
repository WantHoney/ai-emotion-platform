package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.domain.AnalysisSegment;
import com.wuhao.aiemotion.dto.response.AnalysisTaskResultResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PsychologicalRiskScoringServiceTest {

    private final PsychologicalRiskScoringService service =
            new PsychologicalRiskScoringService(new InterventionAdviceService());

    @Test
    void shouldReturnNormalRiskWhenSegmentsAreCalm() {
        List<AnalysisSegment> segments = List.of(
                segment(0, 1000, "happy", 0.90),
                segment(1000, 2000, "neutral", 0.92)
        );

        AnalysisTaskResultResponse.RiskAssessmentPayload risk = service.evaluate(segments);

        assertEquals("NORMAL", risk.risk_level());
        assertEquals(0.0, risk.p_sad());
        assertEquals(0.0, risk.p_angry());
        assertEquals(0.5, risk.p_happy());
        assertEquals(0.0, risk.text_neg());
    }

    @Test
    void shouldReturnHighRiskWhenSadAndAngryDominates() {
        List<AnalysisSegment> segments = List.of(
                segment(0, 6000, "sad", 0.20),
                segment(6000, 10000, "angry", 0.95)
        );

        AnalysisTaskResultResponse.RiskAssessmentPayload risk = service.evaluate(segments);

        assertEquals("NORMAL", risk.risk_level());
        assertEquals(0.6, risk.p_sad());
        assertEquals(0.4, risk.p_angry());
        assertEquals(0.0, risk.p_happy());
    }

    @Test
    void shouldClampTextNegAndScoreRange() {
        List<AnalysisSegment> segments = List.of(
                segment(0, 1000, "sad", 0.1)
        );

        AnalysisTaskResultResponse.RiskAssessmentPayload risk = service.evaluate(segments, 10.0);

        assertEquals(1.0, risk.text_neg());
        assertEquals(64.25, risk.risk_score());
        assertEquals("ATTENTION", risk.risk_level());
    }

    @Test
    void shouldUseAudioSummaryOverridesAndTextConflictDiscount() {
        List<AnalysisSegment> segments = List.of(
                segment(0, 1000, "sad", 0.1)
        );

        AnalysisTaskResultResponse.RiskAssessmentPayload risk =
                service.evaluate(segments, 0.3008, 0.0393, 0.4618, 0.585);

        assertEquals(0.3008, risk.p_sad());
        assertEquals(0.0393, risk.p_angry());
        assertEquals(0.4618, risk.p_happy());
        assertEquals(0.4388, risk.text_neg());
        assertEquals(15.36, risk.risk_score());
        assertEquals("NORMAL", risk.risk_level());
    }

    @Test
    void shouldKeepTextNegWhenSadDominates() {
        List<AnalysisSegment> segments = List.of(
                segment(0, 1000, "sad", 0.1)
        );

        AnalysisTaskResultResponse.RiskAssessmentPayload risk =
                service.evaluate(segments, 0.72, 0.08, 0.10, 0.60);

        assertEquals(0.60, risk.text_neg());
        assertEquals("ATTENTION", risk.risk_level());
    }

    private AnalysisSegment segment(int startMs, int endMs, String emotionCode, double confidence) {
        return new AnalysisSegment(1L, 1L, startMs, endMs, emotionCode, confidence, LocalDateTime.now());
    }
}
