package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.integration.ai.local.OllamaNarrativeClient;
import com.wuhao.aiemotion.integration.text.TextSentimentClient;
import com.wuhao.aiemotion.integration.text.TextSentimentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TranscriptSemanticScoringService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptSemanticScoringService.class);
    private static final List<String> SENTIMENT_LABELS = List.of("negative", "neutral", "positive");
    private static final List<String> EMOTION4_LABELS = List.of("ANG", "HAP", "NEU", "SAD");
    private static final double EPS = 1e-9D;

    private final AnalysisTextScoringProperties properties;
    private final AnalysisNarrativeProperties narrativeProperties;
    private final OllamaNarrativeClient ollamaNarrativeClient;
    private final TextSentimentClient textSentimentClient;
    private final ObjectMapper objectMapper;

    public TranscriptSemanticScoringService(AnalysisTextScoringProperties properties,
                                            AnalysisNarrativeProperties narrativeProperties,
                                            OllamaNarrativeClient ollamaNarrativeClient,
                                            TextSentimentClient textSentimentClient,
                                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.narrativeProperties = narrativeProperties;
        this.ollamaNarrativeClient = ollamaNarrativeClient;
        this.textSentimentClient = textSentimentClient;
        this.objectMapper = objectMapper;
    }

    public TextSentimentResponse score(String transcript, String languageHint, long timeoutMs) {
        if (transcript == null || transcript.isBlank()) {
            return null;
        }
        String routeLanguage = normalizeLanguageHint(languageHint);
        if (routeLanguage == null) {
            routeLanguage = normalizeLanguageHint(properties.getLanguage());
        }

        if (shouldUseSemanticLlm(routeLanguage)) {
            try {
                return scoreWithOllama(transcript, routeLanguage);
            } catch (Exception ex) {
                log.warn("semantic text scoring fallback to ser-service: language={}, reason={}",
                        routeLanguage,
                        truncate(ex.getMessage()));
            }
        }

        if (!properties.isFallbackToSer()) {
            return null;
        }
        return textSentimentClient.score(transcript, routeLanguage, timeoutMs);
    }

    private boolean shouldUseSemanticLlm(String languageHint) {
        if (!properties.isEnabled()) {
            return false;
        }
        if (!"ollama".equalsIgnoreCase(properties.getProvider())) {
            return false;
        }
        return "zh".equalsIgnoreCase(languageHint);
    }

    private TextSentimentResponse scoreWithOllama(String transcript, String routeLanguage) {
        String payload = buildUserPayload(transcript, routeLanguage);
        String responseJson = ollamaNarrativeClient.chat(buildSystemPrompt(), payload, properties.getTemperature());
        SemanticScoreResponse raw = parseResponse(responseJson);

        Map<String, Double> sentimentScores = normalizeScores(raw.scores(), SENTIMENT_LABELS, raw.label(), "neutral");
        Map<String, Double> emotion4Scores = normalizeScores(raw.emotion4Scores(), EMOTION4_LABELS, raw.emotion4Label(), "NEU");
        String sentimentLabel = maxKey(sentimentScores, "neutral");
        String emotion4Label = maxKey(emotion4Scores, "NEU");
        double emotion4Confidence = emotion4Scores.getOrDefault(emotion4Label, 0.0D);
        double negativeScore = clamp01(sentimentScores.getOrDefault("negative", 0.0D));

        Map<String, Double> rawScores = new LinkedHashMap<>();
        rawScores.put("GEMMA_NEG", sentimentScores.getOrDefault("negative", 0.0D));
        rawScores.put("GEMMA_NEU", sentimentScores.getOrDefault("neutral", 0.0D));
        rawScores.put("GEMMA_POS", sentimentScores.getOrDefault("positive", 0.0D));
        rawScores.put("GEMMA_ANG", emotion4Scores.getOrDefault("ANG", 0.0D));
        rawScores.put("GEMMA_HAP", emotion4Scores.getOrDefault("HAP", 0.0D));
        rawScores.put("GEMMA_NEU4", emotion4Scores.getOrDefault("NEU", 0.0D));
        rawScores.put("GEMMA_SAD", emotion4Scores.getOrDefault("SAD", 0.0D));

        return new TextSentimentResponse(
                sentimentLabel,
                negativeScore,
                sentimentScores,
                true,
                emotion4Scores,
                1.0D,
                emotion4Label,
                emotion4Confidence,
                "GEMMA_" + emotion4Label,
                emotion4Confidence,
                rawScores,
                new TextSentimentResponse.Meta(
                        "ollama",
                        narrativeProperties.getOllama().getModel(),
                        routeLanguage,
                        "semantic_llm_v1"
                )
        );
    }

    private SemanticScoreResponse parseResponse(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, SemanticScoreResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("semantic text scoring json parse failed", ex);
        }
    }

    private String buildSystemPrompt() {
        return """
                你是中文语音转写文本的情绪评分器。
                你只能基于输入文本语义给出结构化分数，不参考任何音频模型结果。
                你必须遵守以下规则：
                1. 只输出 RFC8259 JSON，不要输出 Markdown、说明文字或注释。
                2. 使用简体中文理解，但输出字段名必须严格保持英文。
                3. scores 必须包含 negative、neutral、positive 三个字段，且和为 1。
                4. emotion4Scores 必须包含 ANG、HAP、NEU、SAD 四个字段，且和为 1。
                5. negativeScore 必须与 scores.negative 保持一致。
                6. label 只能是 negative、neutral、positive。
                7. emotion4Label 只能是 ANG、HAP、NEU、SAD。
                8. 对问句、测试句、客观描述、无明显情绪倾向文本，优先判为 neutral / NEU。
                9. 明显开心、轻松、满足、舒适等正向表达，优先给 HAP 和 positive。
                10. 明显生气、愤怒、恼火等表达，优先给 ANG 和 negative。
                11. 明显难过、低落、委屈、悲伤等表达，优先给 SAD 和 negative。
                输出 JSON 结构：
                {
                  "label": "negative|neutral|positive",
                  "negativeScore": 0.0,
                  "scores": {
                    "negative": 0.0,
                    "neutral": 0.0,
                    "positive": 0.0
                  },
                  "emotion4Scores": {
                    "ANG": 0.0,
                    "HAP": 0.0,
                    "NEU": 0.0,
                    "SAD": 0.0
                  },
                  "emotion4Label": "ANG|HAP|NEU|SAD"
                }
                """;
    }

    private String buildUserPayload(String transcript, String routeLanguage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("language", routeLanguage);
        payload.put("transcript", buildTranscriptExcerpt(transcript));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to build semantic text scoring payload", ex);
        }
    }

    private String buildTranscriptExcerpt(String transcript) {
        String normalized = transcript == null ? "" : transcript.replace("\r", " ").replace("\n", " ").trim();
        int maxChars = Math.max(80, properties.getMaxTranscriptChars());
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars);
    }

    private Map<String, Double> normalizeScores(Map<String, Double> scores,
                                                List<String> labels,
                                                String preferredLabel,
                                                String defaultLabel) {
        Map<String, Double> normalized = new LinkedHashMap<>();
        double total = 0.0D;
        for (String label : labels) {
            double value = clamp01(findValue(scores, label));
            normalized.put(label, value);
            total += value;
        }
        if (total <= EPS) {
            String fallbackLabel = labels.contains(preferredLabel) ? preferredLabel : defaultLabel;
            for (String label : labels) {
                normalized.put(label, label.equalsIgnoreCase(fallbackLabel) ? 1.0D : 0.0D);
            }
            return normalized;
        }
        for (String label : labels) {
            normalized.put(label, normalized.get(label) / total);
        }
        return normalized;
    }

    private double findValue(Map<String, Double> scores, String key) {
        if (scores == null || scores.isEmpty() || key == null) {
            return 0.0D;
        }
        Double exact = scores.get(key);
        if (exact != null) {
            return exact;
        }
        Double upper = scores.get(key.toUpperCase());
        if (upper != null) {
            return upper;
        }
        Double lower = scores.get(key.toLowerCase());
        if (lower != null) {
            return lower;
        }
        return 0.0D;
    }

    private String maxKey(Map<String, Double> scores, String fallback) {
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(fallback);
    }

    private String normalizeLanguageHint(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        String normalized = language.trim().toLowerCase();
        if (normalized.startsWith("zh")) {
            return "zh";
        }
        if (normalized.startsWith("en")) {
            return "en";
        }
        return null;
    }

    private double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "unknown";
        }
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    private record SemanticScoreResponse(
            String label,
            Double negativeScore,
            Map<String, Double> scores,
            Map<String, Double> emotion4Scores,
            String emotion4Label
    ) {
    }
}
