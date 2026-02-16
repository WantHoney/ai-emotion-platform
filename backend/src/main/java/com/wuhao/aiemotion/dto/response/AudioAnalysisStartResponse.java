package com.wuhao.aiemotion.dto.response;

public record AudioAnalysisStartResponse(
        long analysisId,
        long audioId,
        String status
) {}
