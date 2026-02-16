package com.wuhao.aiemotion.domain;

import java.time.LocalDateTime;

public record AnalysisTask(
        long id,
        Long audioFileId,
        String status,
        int attemptCount,
        Integer maxAttempts,
        String traceId,
        LocalDateTime nextRunAt,
        LocalDateTime lockedAt,
        String lockedBy,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs,
        Long serLatencyMs,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
