package com.wuhao.aiemotion.dto.response;

import java.util.List;

public record AudioAnalysisListResponse(
        long total,
        int page,
        int size,
        List<Item> items
) {
    public record Item(
            long id,
            long audioId,
            String modelName,
            String modelVersion,
            String status,
            String createdAt
    ) {}
}
