package com.wuhao.aiemotion.domain;

import java.time.LocalDateTime;

public record Article(
        Long id,
        String title,
        String coverImageUrl,
        String summary,
        String recommendReason,
        String fitFor,
        String highlights,
        Integer readingMinutes,
        String category,
        String sourceName,
        String sourceUrl,
        String contentUrl,
        Boolean isExternal,
        String difficultyTag,
        Integer sortOrder,
        Boolean recommended,
        Boolean enabled,
        String seedKey,
        String dataSource,
        Boolean isActive,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
