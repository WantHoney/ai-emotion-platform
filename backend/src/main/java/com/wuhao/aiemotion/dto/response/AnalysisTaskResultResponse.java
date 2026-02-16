package com.wuhao.aiemotion.dto.response;

import java.util.List;

public record AnalysisTaskResultResponse(
        AnalysisResultPayload analysis_result,
        List<AnalysisSegmentPayload> analysis_segment,
        long segments_total,
        boolean segments_truncated
) {

    public record RiskAssessmentPayload(
            double risk_score,
            String risk_level,
            String advice_text,
            double p_sad,
            double p_angry,
            double var_conf,
            double text_neg
    ) {
    }

    public record AnalysisResultPayload(
            long id,
            long task_id,
            String model_name,
            String overall_emotion_code,
            Double overall_confidence,
            Integer duration_ms,
            Integer sample_rate,
            String raw_json,
            String transcript,
            RiskAssessmentPayload risk_assessment,
            String created_at
    ) {
    }

    public record AnalysisSegmentPayload(
            long id,
            long task_id,
            int start_ms,
            int end_ms,
            String emotion_code,
            double confidence,
            String created_at
    ) {
    }
}
