package com.wuhao.aiemotion.domain;

import java.time.LocalDateTime;

public record Banner(
        Long id,
        String title,
        String imageUrl,
        String linkUrl,
        Integer sortOrder,
        Boolean recommended,
        Boolean enabled,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
