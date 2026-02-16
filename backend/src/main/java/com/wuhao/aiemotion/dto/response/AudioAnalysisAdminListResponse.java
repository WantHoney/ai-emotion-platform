package com.wuhao.aiemotion.dto.response;

import java.util.List;

public record AudioAnalysisAdminListResponse(
        long total,
        int page,
        int size,
        List<Item> items
) {
    public record Item(
            long id,
            long audioId,
            String audioOriginalName, // ✅ NEW
            String modelName,
            String modelVersion,
            String status,
            String createdAt,
            String updatedAt
    ) {}
}
