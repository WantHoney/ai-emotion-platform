package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.integration.ai.local.OllamaNarrativeClient;
import com.wuhao.aiemotion.integration.text.TextSentimentClient;
import com.wuhao.aiemotion.integration.text.TextSentimentResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptSemanticScoringServiceTest {

    @Test
    void shouldUseOllamaSemanticScoresForChineseTranscript() {
        AnalysisTextScoringProperties properties = new AnalysisTextScoringProperties();
        AnalysisNarrativeProperties narrativeProperties = new AnalysisNarrativeProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        OllamaNarrativeClient ollamaClient = new OllamaNarrativeClient(null, narrativeProperties) {
            @Override
            public String chat(String systemPrompt, String userPrompt) {
                return """
                        {
                          "label":"positive",
                          "negativeScore":0.08,
                          "scores":{"negative":0.08,"neutral":0.12,"positive":0.80},
                          "emotion4Scores":{"ANG":0.04,"HAP":0.78,"NEU":0.12,"SAD":0.06},
                          "emotion4Label":"HAP"
                        }
                        """;
            }

            @Override
            public String chat(String systemPrompt, String userPrompt, Double temperatureOverride) {
                return chat(systemPrompt, userPrompt);
            }
        };
        TextSentimentClient fallbackClient = new TextSentimentClient(null, null) {
            @Override
            public TextSentimentResponse score(String text, String language, long timeoutMs) {
                throw new AssertionError("fallback client should not be called");
            }
        };

        TranscriptSemanticScoringService service = new TranscriptSemanticScoringService(
                properties,
                narrativeProperties,
                ollamaClient,
                fallbackClient,
                objectMapper
        );

        TextSentimentResponse response = service.score("今天很开心，太阳很好，出去散步了", "zh", 10_000L);

        assertNotNull(response);
        assertEquals("positive", response.label());
        assertEquals("HAP", response.emotion4Label());
        assertTrue(Boolean.TRUE.equals(response.emotion4Ready()));
        assertEquals(1.0D, response.mappedMass());
        assertEquals("ollama", response.meta().engine());
        assertEquals("semantic_llm_v1", response.meta().routingStrategy());
        assertEquals(0.80D, response.scores().get("positive"), 1e-6);
    }

    @Test
    void shouldFallbackToSerTextModelWhenOllamaFails() {
        AnalysisTextScoringProperties properties = new AnalysisTextScoringProperties();
        AnalysisNarrativeProperties narrativeProperties = new AnalysisNarrativeProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        OllamaNarrativeClient ollamaClient = new OllamaNarrativeClient(null, narrativeProperties) {
            @Override
            public String chat(String systemPrompt, String userPrompt) {
                throw new IllegalStateException("ollama offline");
            }

            @Override
            public String chat(String systemPrompt, String userPrompt, Double temperatureOverride) {
                throw new IllegalStateException("ollama offline");
            }
        };
        TextSentimentClient fallbackClient = new TextSentimentClient(null, null) {
            @Override
            public TextSentimentResponse score(String text, String language, long timeoutMs) {
                return new TextSentimentResponse(
                        "neutral",
                        0.15D,
                        Map.of("negative", 0.15D, "neutral", 0.7D, "positive", 0.15D),
                        false,
                        Map.of("ANG", 0.05D, "HAP", 0.15D, "NEU", 0.7D, "SAD", 0.10D),
                        0.0D,
                        "NEU",
                        0.7D,
                        "FALLBACK_NEU",
                        0.7D,
                        Map.of("fallback", 1.0D),
                        new TextSentimentResponse.Meta("hf", "fallback", "zh", "fallback")
                );
            }
        };

        TranscriptSemanticScoringService service = new TranscriptSemanticScoringService(
                properties,
                narrativeProperties,
                ollamaClient,
                fallbackClient,
                objectMapper
        );

        TextSentimentResponse response = service.score("这是一段普通的测试文本", "zh", 10_000L);

        assertNotNull(response);
        assertEquals("neutral", response.label());
        assertEquals("fallback", response.meta().model());
    }
}
