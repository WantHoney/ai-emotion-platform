package com.wuhao.aiemotion.service;

import java.util.List;

public record NarrativePayload(
        String status,
        String provider,
        String model,
        String summary,
        String explanation,
        AdviceBuckets adviceBuckets,
        List<String> personalizedAdvice,
        String safetyNotice,
        String error
) {
    public record AdviceBuckets(
            List<String> instant,
            List<String> longTerm,
            List<String> resource
    ) {
    }
}
