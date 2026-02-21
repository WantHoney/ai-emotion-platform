package com.wuhao.aiemotion.dto.response;

import java.util.List;
import java.util.Map;

public record TaskRealtimeSnapshotResponse(
        String event,
        long taskId,
        String taskNo,
        String status,
        int attemptCount,
        Integer maxAttempts,
        String traceId,
        String nextRunAt,
        String updatedAt,
        String errorMessage,
        boolean terminal,
        RiskSummary risk,
        ProgressSummary progress,
        List<RiskCurvePoint> curve
) {

    public record RiskSummary(
            double riskScore,
            String riskLevel,
            double pSad,
            double pAngry,
            double varConf,
            double textNeg
    ) {
    }

    public record ProgressSummary(
            String phase,
            String message,
            long sequence,
            long emittedAtMs,
            Map<String, Object> details
    ) {
    }

    public record RiskCurvePoint(
            int index,
            int startMs,
            int endMs,
            String emotion,
            double confidence,
            double riskIndex
    ) {
    }
}
