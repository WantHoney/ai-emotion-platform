package com.wuhao.aiemotion.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PsyCenter(
        Long id,
        String name,
        String cityCode,
        String cityName,
        String district,
        String address,
        String phone,
        BigDecimal latitude,
        BigDecimal longitude,
        String sourceName,
        String sourceUrl,
        String sourceLevel,
        Boolean recommended,
        Boolean enabled,
        String seedKey,
        String dataSource,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
