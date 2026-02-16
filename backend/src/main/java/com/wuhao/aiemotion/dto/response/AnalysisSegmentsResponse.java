package com.wuhao.aiemotion.dto.response;

import java.util.List;

public record AnalysisSegmentsResponse(
        long taskId,
        long fromMs,
        long toMs,
        int limit,
        int offset,
        long total,
        List<AnalysisTaskResultResponse.AnalysisSegmentPayload> items
) {
}
