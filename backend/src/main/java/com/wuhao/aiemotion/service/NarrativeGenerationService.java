package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.dto.response.AnalysisTaskResultResponse;
import com.wuhao.aiemotion.integration.ai.local.OllamaNarrativeClient;
import com.wuhao.aiemotion.integration.ser.SerAnalyzeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class NarrativeGenerationService {

    private static final Logger log = LoggerFactory.getLogger(NarrativeGenerationService.class);
    private static final int MAX_ERROR_LENGTH = 400;

    private final AnalysisNarrativeProperties properties;
    private final OllamaNarrativeClient ollamaNarrativeClient;
    private final ObjectMapper objectMapper;

    public NarrativeGenerationService(AnalysisNarrativeProperties properties,
                                      OllamaNarrativeClient ollamaNarrativeClient,
                                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.ollamaNarrativeClient = ollamaNarrativeClient;
        this.objectMapper = objectMapper;
    }

    public NarrativePayload generate(long taskId,
                                     SerAnalyzeResponse response,
                                     String transcript,
                                     AnalysisTaskResultResponse.RiskAssessmentPayload riskAssessment,
                                     ConsistencyDecision decision) {
        NarrativePayload.AdviceBuckets fallbackBuckets = toFallbackAdviceBuckets(
                riskAssessment == null ? null : riskAssessment.advice_text()
        );
        List<String> fallbackAdvice = flattenAdvice(fallbackBuckets);
        String safetyNotice = cleanText(properties.getSafetyNotice());
        String provider = properties.getProvider();
        String model = properties.getOllama().getModel();

        if (!properties.isEnabled()) {
            return new NarrativePayload(
                    "disabled",
                    provider,
                    model,
                    null,
                    null,
                    fallbackBuckets,
                    fallbackAdvice,
                    safetyNotice,
                    null
            );
        }

        if (!"ollama".equalsIgnoreCase(provider)) {
            return new NarrativePayload(
                    "fallback",
                    provider,
                    model,
                    null,
                    null,
                    fallbackBuckets,
                    fallbackAdvice,
                    safetyNotice,
                    "unsupported narrative provider: " + provider
            );
        }

        try {
            String promptPayload = buildPromptPayload(taskId, response, transcript, riskAssessment, fallbackAdvice, decision);
            String responseJson = ollamaNarrativeClient.chat(buildSystemPrompt(), buildUserPrompt(promptPayload));
            LlmNarrativeResponse llmResponse = objectMapper.readValue(responseJson, LlmNarrativeResponse.class);

            String summary = cleanText(llmResponse.summary());
            String explanation = cleanText(llmResponse.explanation());
            if (isBlank(summary) && isBlank(explanation)) {
                throw new IllegalStateException("narrative summary and explanation are both empty");
            }

            NarrativePayload.AdviceBuckets adviceBuckets = normalizeGeneratedBuckets(llmResponse.adviceBuckets(), fallbackBuckets);
            List<String> personalizedAdvice = flattenAdvice(adviceBuckets);
            String generatedSafetyNotice = cleanText(llmResponse.safetyNotice());

            return new NarrativePayload(
                    "ready",
                    provider,
                    model,
                    summary,
                    explanation,
                    adviceBuckets,
                    personalizedAdvice,
                    isBlank(generatedSafetyNotice) ? safetyNotice : generatedSafetyNotice,
                    null
            );
        } catch (Exception ex) {
            log.warn("local narrative generation fallback: taskId={}, provider={}, model={}, reason={}",
                    taskId,
                    provider,
                    model,
                    truncate(ex.getMessage()));
            return new NarrativePayload(
                    "fallback",
                    provider,
                    model,
                    null,
                    null,
                    fallbackBuckets,
                    fallbackAdvice,
                    safetyNotice,
                    truncate(ex.getMessage())
            );
        }
    }

    private String buildSystemPrompt() {
        return """
                你是一个多模态心理风险分析系统的解释层助手。
                你只能基于输入事实生成解释、摘要和建议，不能改动或质疑已经给定的 risk_score、risk_level、overall_emotion。
                你必须遵守以下规则：
                1. 只返回 RFC8259 JSON，不要输出任何 Markdown、标题或额外说明。
                2. 使用简体中文。
                3. 不要给出医疗诊断，不要夸大风险，不要承诺治愈。
                4. 如果 transcriptExcerpt 为空，不要虚构用户原话。
                5. summary 用 2 到 3 句话概括本次结果。
                6. explanation 用 2 到 4 句话解释为什么会得到当前判断，并点出主要风险因子。
                7. adviceBuckets.instant 提供 1 到 2 条当前就能做的建议。
                8. adviceBuckets.longTerm 提供 1 到 2 条较长期的调节建议。
                9. adviceBuckets.resource 提供 1 到 2 条支持资源建议；如果 risk_level 是 HIGH，至少有一条建议联系可信任的人或专业支持。
                10. safetyNotice 保持审慎、支持性的提醒语气。
                输出 JSON 结构：
                {
                  "summary": "string",
                  "explanation": "string",
                  "adviceBuckets": {
                    "instant": ["string"],
                    "longTerm": ["string"],
                    "resource": ["string"]
                  },
                  "safetyNotice": "string"
                }
                """;
    }

    private String buildUserPrompt(String promptPayload) {
        return "请根据下面的结构化结果生成更贴切但边界受控的解释与建议。输入 JSON: " + promptPayload;
    }

    private String buildPromptPayload(long taskId,
                                      SerAnalyzeResponse response,
                                      String transcript,
                                      AnalysisTaskResultResponse.RiskAssessmentPayload riskAssessment,
                                      List<String> fallbackAdvice,
                                      ConsistencyDecision decision) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("taskId", taskId);
        root.put("language", properties.getLanguage());
        root.put("overallEmotion", resolveOverallEmotion(decision, response));
        root.put("risk", buildRiskNode(riskAssessment));
        root.put("serFusion", buildFusionNode(response));
        root.put("decision", buildDecisionNode(decision));
        root.put("topSegments", buildTopSegments(response));
        root.put("transcriptExcerpt", buildTranscriptExcerpt(transcript));
        root.put("baselineAdvice", fallbackAdvice);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to build narrative prompt payload", e);
        }
    }

    private Map<String, Object> buildRiskNode(AnalysisTaskResultResponse.RiskAssessmentPayload riskAssessment) {
        Map<String, Object> risk = new LinkedHashMap<>();
        if (riskAssessment == null) {
            return risk;
        }
        risk.put("score", riskAssessment.risk_score());
        risk.put("level", riskAssessment.risk_level());
        risk.put("pSad", riskAssessment.p_sad());
        risk.put("pAngry", riskAssessment.p_angry());
        risk.put("varConf", riskAssessment.var_conf());
        risk.put("textNeg", riskAssessment.text_neg());
        return risk;
    }

    private Map<String, Object> buildFusionNode(SerAnalyzeResponse response) {
        Map<String, Object> fusion = new LinkedHashMap<>();
        if (response == null) {
            return fusion;
        }
        if (response.fusion() != null) {
            if (!isBlank(response.fusion().label())) {
                fusion.put("label", response.fusion().label());
            }
            if (response.fusion().confidence() != null) {
                fusion.put("confidence", response.fusion().confidence());
            }
            if (response.fusion().ready() != null) {
                fusion.put("ready", response.fusion().ready());
            }
        }
        if (response.overall() != null) {
            fusion.put("overallEmotion", response.overall().emotionCode());
            fusion.put("overallConfidence", response.overall().confidence());
        }
        return fusion;
    }

    private List<Map<String, Object>> buildTopSegments(SerAnalyzeResponse response) {
        if (response == null || response.segments() == null || response.segments().isEmpty()) {
            return List.of();
        }
        return response.segments().stream()
                .sorted(Comparator.comparingDouble(SerAnalyzeResponse.Segment::confidence).reversed())
                .limit(Math.max(1, properties.getMaxSegments()))
                .map(segment -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("startMs", segment.startMs());
                    item.put("endMs", segment.endMs());
                    item.put("emotion", segment.emotionCode());
                    item.put("confidence", segment.confidence());
                    return item;
                })
                .toList();
    }

    private String buildTranscriptExcerpt(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return null;
        }
        String normalized = transcript.replace("\r", " ").replace("\n", " ").trim();
        int maxChars = Math.max(80, properties.getMaxTranscriptChars());
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }

    private Map<String, Object> buildDecisionNode(ConsistencyDecision decision) {
        Map<String, Object> node = new LinkedHashMap<>();
        if (decision == null) {
            return node;
        }
        node.put("code", decision.code());
        node.put("status", decision.status());
        node.put("reason", decision.reason());
        node.put("source", decision.source());
        node.put("baseEmotionCode", decision.baseEmotionCode());
        node.put("baseConfidence", decision.baseConfidence());
        if (decision.audit() != null) {
            node.put("positiveHits", decision.audit().positiveHits());
            node.put("negativeHits", decision.audit().negativeHits());
            node.put("transcriptExcerpt", decision.audit().transcriptExcerpt());
        }
        return node;
    }

    private String resolveOverallEmotion(ConsistencyDecision decision, SerAnalyzeResponse response) {
        if (decision != null && !isBlank(decision.code())) {
            return decision.code();
        }
        if (response != null && response.fusion() != null && !isBlank(response.fusion().label())) {
            return response.fusion().label();
        }
        if (response != null && response.overall() != null && !isBlank(response.overall().emotionCode())) {
            return response.overall().emotionCode();
        }
        return null;
    }

    private NarrativePayload.AdviceBuckets toFallbackAdviceBuckets(String adviceText) {
        List<String> items = splitAdviceText(adviceText);
        return new NarrativePayload.AdviceBuckets(
                copySlice(items, 0, 2),
                copySlice(items, 2, 4),
                copySlice(items, 4, 6)
        );
    }

    private NarrativePayload.AdviceBuckets normalizeGeneratedBuckets(LlmAdviceBuckets buckets,
                                                                     NarrativePayload.AdviceBuckets fallbackBuckets) {
        List<String> instant = normalizeBucketItems(buckets == null ? null : buckets.instant());
        List<String> longTerm = normalizeBucketItems(buckets == null ? null : buckets.longTerm());
        List<String> resource = normalizeBucketItems(buckets == null ? null : buckets.resource());

        return new NarrativePayload.AdviceBuckets(
                instant.isEmpty() ? fallbackBuckets.instant() : instant,
                longTerm.isEmpty() ? fallbackBuckets.longTerm() : longTerm,
                resource.isEmpty() ? fallbackBuckets.resource() : resource
        );
    }

    private List<String> splitAdviceText(String adviceText) {
        if (adviceText == null || adviceText.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (String item : adviceText.split("[\\r\\n;；]+")) {
            String cleaned = cleanText(item);
            if (!isBlank(cleaned)) {
                items.add(cleaned);
            }
        }
        return new ArrayList<>(items);
    }

    private List<String> normalizeBucketItems(List<String> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String item : items) {
            String cleaned = cleanText(item);
            if (!isBlank(cleaned)) {
                normalized.add(cleaned);
            }
            if (normalized.size() >= 2) {
                break;
            }
        }
        return new ArrayList<>(normalized);
    }

    private List<String> flattenAdvice(NarrativePayload.AdviceBuckets buckets) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        if (buckets != null) {
            if (buckets.instant() != null) {
                items.addAll(buckets.instant());
            }
            if (buckets.longTerm() != null) {
                items.addAll(buckets.longTerm());
            }
            if (buckets.resource() != null) {
                items.addAll(buckets.resource());
            }
        }
        return new ArrayList<>(items);
    }

    private List<String> copySlice(List<String> items, int start, int end) {
        if (items == null || items.isEmpty() || start >= items.size()) {
            return List.of();
        }
        return new ArrayList<>(items.subList(start, Math.min(end, items.size())));
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace("\r", " ").replace("\n", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "unknown error";
        }
        return value.length() > MAX_ERROR_LENGTH ? value.substring(0, MAX_ERROR_LENGTH) : value;
    }

    private record LlmNarrativeResponse(
            String summary,
            String explanation,
            LlmAdviceBuckets adviceBuckets,
            String safetyNotice
    ) {
    }

    private record LlmAdviceBuckets(
            List<String> instant,
            List<String> longTerm,
            List<String> resource
    ) {
    }
}
