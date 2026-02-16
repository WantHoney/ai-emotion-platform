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
        Boolean recommended,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
