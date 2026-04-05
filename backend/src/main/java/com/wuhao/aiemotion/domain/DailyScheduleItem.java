package com.wuhao.aiemotion.domain;

import java.time.LocalDateTime;

public record DailyScheduleItem(
        Long id,
        Long scheduleId,
        String contentType,
        Long contentId,
        String slotRole,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
