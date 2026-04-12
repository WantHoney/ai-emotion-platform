package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.dto.response.AnalysisTaskResultResponse;
import com.wuhao.aiemotion.dto.response.AnalysisTaskStatusResponse;
import com.wuhao.aiemotion.dto.response.DecisionResponse;
import com.wuhao.aiemotion.dto.response.TaskRealtimeSnapshotResponse;
import com.wuhao.aiemotion.integration.text.TextSentimentResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionSerializationRegressionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeTextSentimentMappedMass() throws Exception {
        TextSentimentResponse response = new TextSentimentResponse(
                "negative",
                0.3529D,
                Map.of("negative", 0.3529D, "neutral", 0.3224D, "positive", 0.3247D),
                false,
                Map.of("ANG", 0.17645D, "HAP", 0.3247D, "NEU", 0.3224D, "SAD", 0.17645D),
                0.3224D,
                "NEU",
                0.3224D,
                "negative",
                0.3529D,
                Map.of("negative", 0.3529D),
                new TextSentimentResponse.Meta("hf", "zh-model", "zh", "zh")
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertEquals(0.3224D, json.path("mappedMass").asDouble(), 0.0001D);
    }

    @Test
    void shouldSerializeDecisionAuditWithStableFieldNames() throws Exception {
        ConsistencyDecision decision = new ConsistencyDecision(
                ConsistencyGuardService.LOW_CONSISTENCY_CODE,
                ConsistencyGuardService.LOW_CONSISTENCY_STATUS,
                ConsistencyGuardService.SAD_POSITIVE_CONFLICT_REASON,
                ConsistencyGuardService.CONSISTENCY_GUARD_SOURCE,
                "SAD",
                0.9690D,
                new ConsistencyDecision.Audit(
                        ConsistencyGuardService.SAD_POSITIVE_CONFLICT_RULE,
                        true,
                        37L,
                        101L,
                        "trace-37",
                        "SAD",
                        0.9690D,
                        "SAD",
                        0.9987D,
                        "SAD",
                        0.9690D,
                        0.2646D,
                        "negative",
                        0.3529D,
                        0.3224D,
                        false,
                        List.of("开心", "休息得很好"),
                        List.of(),
                        "今天真的很开心，晚上休息得很好。",
                        new ConsistencyDecision.Thresholds(0.98D, 0.45D)
                )
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(decision));

        assertEquals("LOW_CONSISTENCY", json.path("code").asText());
        assertEquals(0.3224D, json.at("/audit/mappedMass").asDouble(), 0.0001D);
        assertEquals("开心", json.at("/audit/positiveHits/0").asText());
        assertTrue(json.at("/audit/transcriptExcerpt").isTextual());
    }

    @Test
    void shouldSerializeDecisionAcrossApiResponses() throws Exception {
        DecisionResponse decision = new DecisionResponse(
                "LOW_CONSISTENCY",
                "LOW_CONSISTENCY",
                "voice_sad_high_conflicts_with_positive_text",
                "CONSISTENCY_GUARD",
                "SAD",
                0.9690D
        );
        AnalysisTaskResultResponse resultResponse = new AnalysisTaskResultResponse(
                new AnalysisTaskResultResponse.AnalysisResultPayload(
                        1L,
                        37L,
                        "model-a",
                        "SAD",
                        decision,
                        0.9987D,
                        12180,
                        16000,
                        "{\"decision\":{}}",
                        "今天真的很开心",
                        new AnalysisTaskResultResponse.RiskAssessmentPayload(37.58D, "NORMAL", "复核提示", 1.0D, 0.0D, 0.0D, 0.0D, 0.2646D),
                        "2026-04-05 10:00:00"
                ),
                List.of(),
                0L,
                false
        );
        AnalysisTaskStatusResponse statusResponse = new AnalysisTaskStatusResponse(
                37L,
                "U0001-20260405-0001",
                "SUCCESS",
                1,
                4,
                "trace-37",
                null,
                null,
                "2026-04-05 09:59:00",
                "2026-04-05 10:00:00",
                "2026-04-05 09:58:00",
                "2026-04-05 10:00:00",
                new AnalysisTaskStatusResponse.OverallSummary(
                        "SAD",
                        decision,
                        0.9987D,
                        12180,
                        16000,
                        "model-a",
                        new AnalysisTaskResultResponse.RiskAssessmentPayload(37.58D, "NORMAL", "复核提示", 1.0D, 0.0D, 0.0D, 0.0D, 0.2646D)
                )
        );
        TaskRealtimeSnapshotResponse snapshotResponse = new TaskRealtimeSnapshotResponse(
                "snapshot",
                37L,
                "U0001-20260405-0001",
                "SUCCESS",
                1,
                4,
                "trace-37",
                null,
                "2026-04-05 10:00:00",
                null,
                true,
                new TaskRealtimeSnapshotResponse.RiskSummary(37.58D, "NORMAL", 1.0D, 0.0D, 0.0D, 0.2646D),
                decision,
                null,
                List.of()
        );

        JsonNode resultJson = objectMapper.readTree(objectMapper.writeValueAsBytes(resultResponse));
        JsonNode statusJson = objectMapper.readTree(objectMapper.writeValueAsBytes(statusResponse));
        JsonNode snapshotJson = objectMapper.readTree(objectMapper.writeValueAsBytes(snapshotResponse));

        assertEquals("LOW_CONSISTENCY", resultJson.at("/analysis_result/decision/code").asText());
        assertEquals("SAD", resultJson.at("/analysis_result/decision/base_emotion_code").asText());
        assertEquals("LOW_CONSISTENCY", statusJson.at("/overall/decision/code").asText());
        assertEquals("LOW_CONSISTENCY", snapshotJson.at("/decision/code").asText());
        assertEquals(0.9690D, snapshotJson.at("/decision/base_confidence").asDouble(), 0.0001D);
    }
}
