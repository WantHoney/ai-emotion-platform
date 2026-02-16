package com.wuhao.aiemotion.dto.response;

public record AudioAnalysisDetailResponse(
        long id,
        long audioId,
        String modelName,
        String modelVersion,
        String status,
        Object summary,        // ✅ 改成真正的JSON对象（Map/List/Primitive都行）
        String errorMessage,
        String createdAt,
        String updatedAt
) {}
