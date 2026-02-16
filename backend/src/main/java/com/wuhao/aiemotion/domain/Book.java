package com.wuhao.aiemotion.domain;

import java.time.LocalDateTime;

public record Book(
        Long id,
        String title,
        String author,
        String coverImageUrl,
        String description,
        String purchaseUrl,
        Integer sortOrder,
        Boolean recommended,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
