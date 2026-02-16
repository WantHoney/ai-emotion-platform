package com.wuhao.aiemotion.dto.response;

import java.util.List;

public record AudioListResponse(
        long total,
        int page,
        int size,
        List<Item> items
) {
    public record Item(
            long id,
            Long userId,
            String originalName,
            String storedName,
            String url,
            Long sizeBytes,
            Long durationMs,
            String status,
            String createdAt   // 已格式化好的时间字符串
    ) {}
}
