package com.wuhao.aiemotion.domain;

import java.time.LocalDateTime;

public record Quote(
        Long id,
        String content,
        String author,
        Integer sortOrder,
        Boolean recommended,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
