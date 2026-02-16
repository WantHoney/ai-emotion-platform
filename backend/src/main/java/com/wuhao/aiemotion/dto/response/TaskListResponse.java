package com.wuhao.aiemotion.dto.response;

import java.util.List;

public record TaskListResponse(
        long total,
        int page,
        int size,
        List<TaskDTO> items
) {
    public record TaskDTO(
            long id,
            Long audioId,
            String status,
            int attemptCount,
            Integer maxAttempts,
            String errorMessage,
            String traceId,
            String createdAt,
            String updatedAt,
            String startedAt,
            String finishedAt,
            Long durationMs,
            Long serLatencyMs,
            Long resultId
    ) {}
}
