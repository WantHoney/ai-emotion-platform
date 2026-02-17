package com.wuhao.aiemotion.dto.response;

public record AnalysisTaskStartResponse(
        long taskId,
        String taskNo,
        String status
) {
}
