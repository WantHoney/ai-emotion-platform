package com.wuhao.aiemotion.exception;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        String code,
        String message,
        String traceId,
        Instant timestamp,
        String path,
        Map<String, Object> details
) {}
