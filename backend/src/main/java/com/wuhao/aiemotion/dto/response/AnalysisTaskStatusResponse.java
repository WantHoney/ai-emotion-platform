package com.wuhao.aiemotion.dto.response;

public record AnalysisTaskStatusResponse(
        long taskId,
        String task_no,
        String status,
        int attempt_count,
        String next_run_at,
        String error_message,
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
