package com.wuhao.aiemotion.integration.asr;

import java.util.List;

public record AsrTranscribeResponse(
        String text,
        String language,
        List<AsrSegment> segments,
        AsrMeta meta
) {
    public record AsrSegment(
            long startMs,
            long endMs,
            String text
    ) {
    }

    public record AsrMeta(
            String model,
            long durationMs
    ) {
    }
}

