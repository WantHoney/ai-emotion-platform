package com.wuhao.aiemotion.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailySchedule(
        Long id,
        LocalDate scheduleDate,
        String themeKey,
        String themeTitle,
        String themeSubtitle,
        Long quoteId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
