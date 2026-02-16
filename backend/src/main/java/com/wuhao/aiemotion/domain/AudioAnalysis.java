package com.wuhao.aiemotion.domain;

import java.time.LocalDateTime;

public record AudioAnalysis(
        Long id,
        Long audioId,
        String modelName,
        String modelVersion,
        String status,
        String summaryJson,     // JSON字段在Java里先用String承载（最稳）
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
