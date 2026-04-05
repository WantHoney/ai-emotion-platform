package com.wuhao.aiemotion.service;

import java.util.List;

public record TrendInsightPayload(
        String status,
        String provider,
        String model,
        String headline,
        String summary,
        List<String> highlights,
        String note,
        String error
) {
}
