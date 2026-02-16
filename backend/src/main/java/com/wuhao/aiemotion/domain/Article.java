package com.wuhao.aiemotion.domain;

import java.time.LocalDateTime;

public record Article(
        Long id,
        String title,
        String coverImageUrl,
        String summary,
        String contentUrl,
        Integer sortOrder,
        Boolean recommended,
        Boolean enabled,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
