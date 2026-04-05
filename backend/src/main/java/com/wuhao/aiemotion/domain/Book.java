package com.wuhao.aiemotion.domain;

import java.time.LocalDateTime;

public record Book(
        Long id,
        String title,
        String author,
        String coverImageUrl,
        String description,
        String category,
        String recommendReason,
        String fitFor,
        String highlights,
        String purchaseUrl,
        Integer sortOrder,
        Boolean recommended,
        Boolean enabled,
        String seedKey,
        String dataSource,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
