package com.wuhao.aiemotion.dto.response;

import java.util.List;

public record ReportListResponse(
        long total,
        int page,
        int pageSize,
        int size,
        List<ReportDTO> items
) {
    public record ReportDTO(
            long id,
            long taskId,
            String overall,
            List<SegmentDTO> segments,
            RiskDTO risk,
            Double confidence,
            String createdAt,
            AudioMetaDTO audio
    ) {}

    public record SegmentDTO(long startMs, long endMs, String emotion, double confidence) {}

    public record RiskDTO(double score, String level) {}

    public record AudioMetaDTO(long audioId, String originalName, String storedName, String contentType, Long sizeBytes, Long durationMs) {}
}
