package com.wuhao.aiemotion.dto.response;

public record DecisionResponse(
        String code,
        String status,
        String reason,
        String source,
        String base_emotion_code,
        Double base_confidence
) {
}
