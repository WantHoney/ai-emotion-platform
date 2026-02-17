package com.wuhao.aiemotion.dto.response;

public record AnalysisTaskStatusResponse(
        long taskId,
        String task_no,
        String status,
        int attempt_count,
        Integer max_attempts,
        String trace_id,
        String next_run_at,
        String error_message,
        String started_at,
        String finished_at,
        String created_at,
        String updated_at,
        OverallSummary overall
) {
    public record OverallSummary(
            String overall_emotion_code,
            Double overall_confidence,
            Integer duration_ms,
            Integer sample_rate,
            String model_name,
            AnalysisTaskResultResponse.RiskAssessmentPayload risk_assessment
    ) {
    }
}
