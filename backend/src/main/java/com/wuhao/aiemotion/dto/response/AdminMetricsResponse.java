package com.wuhao.aiemotion.dto.response;

public record AdminMetricsResponse(
        long totalTasks,
        long activeTasks,
        double successRate,
        long avgDurationMs,
        long serTimeoutCount
) {
}
