package com.wuhao.aiemotion.integration.ai;

import com.wuhao.aiemotion.domain.AudioFile;

public record AiAnalysisRequest(
        Long analysisId,
        Long audioId,
        AudioFile audioFile,
        String language,
        Integer sampleRate,
        String modelName
) {
    public static AiAnalysisRequest fromAudioId(long analysisId, long audioId, String modelName) {
        return new AiAnalysisRequest(analysisId, audioId, null, null, null, modelName);
    }

    public AiAnalysisRequest withAudioFile(AudioFile audioFile) {
        return new AiAnalysisRequest(analysisId, audioId, audioFile, language, sampleRate, modelName);
    }

    public AiAnalysisRequest withOptions(String language, Integer sampleRate, String modelName) {
        return new AiAnalysisRequest(analysisId, audioId, audioFile, language, sampleRate, modelName);
    }
}
