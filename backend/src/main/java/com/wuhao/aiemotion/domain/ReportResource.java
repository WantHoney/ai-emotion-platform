package com.wuhao.aiemotion.domain;

import java.time.LocalDateTime;

public record ReportResource(
        long id,
        long taskId,
        long audioId,
        String reportJson,
        String riskLevel,
        String overallEmotion,
        LocalDateTime createdAt,
        LocalDateTime deletedAt
) {
}
