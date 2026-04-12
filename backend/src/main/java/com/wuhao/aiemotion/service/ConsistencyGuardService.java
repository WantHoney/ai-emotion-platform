package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.dto.response.AnalysisTaskResultResponse;
import com.wuhao.aiemotion.integration.ser.SerAnalyzeResponse;
import com.wuhao.aiemotion.integration.text.TextSentimentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ConsistencyGuardService {

    private static final Logger log = LoggerFactory.getLogger(ConsistencyGuardService.class);
    public static final String LOW_CONSISTENCY_CODE = "LOW_CONSISTENCY";
    public static final String LOW_CONSISTENCY_STATUS = "LOW_CONSISTENCY";
    public static final String READY_STATUS = "READY";
    public static final String UNAVAILABLE_STATUS = "UNAVAILABLE";
    public static final String UNKNOWN_CODE = "UNKNOWN";
    public static final String BASE_RESULT_SOURCE = "BASE_RESULT";
    public static final String CONSISTENCY_GUARD_SOURCE = "CONSISTENCY_GUARD";
    public static final String SAD_POSITIVE_CONFLICT_RULE = "sad_positive_conflict_v1";
    public static final String SAD_POSITIVE_CONFLICT_REASON = "voice_sad_high_conflicts_with_positive_text";
    public static final String BASE_EMOTION_MISSING_REASON = "base_emotion_missing";
    public static final String LOW_CONSISTENCY_NOTICE = "语音与文本存在明显冲突，本次结论建议复核，不宜直接按单一情绪强判。";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final List<String> DEFAULT_POSITIVE_TERMS = List.of(
            "开心",
            "高兴",
            "快乐",
            "真好",
            "很好",
            "太阳非常好",
            "休息很好",
            "休息的很好"
    );
    private static final List<String> DEFAULT_NEGATIVE_TERMS = List.of(
            "不开心",
            "难过",
            "伤心",
            "痛苦",
            "愤怒",
            "生气",
            "焦虑",
            "绝望",
            "抑郁",
            "压力"
    );

    private final AnalysisConsistencyProperties properties;
    private final ObjectMapper objectMapper;
    private final List<String> positiveTerms;
    private final List<String> negativeTerms;

    public ConsistencyGuardService(AnalysisConsistencyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.positiveTerms = loadTerms(
                properties.getSadPositiveConflict().getPositiveLexiconResource(),
                DEFAULT_POSITIVE_TERMS,
                "positive"
        );
        this.negativeTerms = loadTerms(
                properties.getSadPositiveConflict().getNegativeLexiconResource(),
                DEFAULT_NEGATIVE_TERMS,
                "negative"
        );
    }

    public ConsistencyDecision evaluate(long taskId,
                                        Long audioId,
                                        String traceId,
                                        String transcript,
                                        SerAnalyzeResponse serResponse,
                                        TextSentimentResponse textSentiment,
                                        double fusedTextNeg) {
        BaseResult baseResult = resolveBaseResult(serResponse);
        VoiceResult voiceResult = resolveVoiceResult(serResponse);
        FusionResult fusionResult = resolveFusionResult(serResponse);
        String normalizedTranscript = normalizeTranscript(transcript);

        List<HitMatch> negativeHitMatches = detectHits(normalizedTranscript, negativeTerms, List.of());
        List<HitMatch> positiveHitMatches = detectHits(normalizedTranscript, positiveTerms, negativeHitMatches);
        List<String> positiveHits = toHitTerms(positiveHitMatches);
        List<String> negativeHits = toHitTerms(negativeHitMatches);

        AnalysisConsistencyProperties.SadPositiveConflict ruleProperties = properties.getSadPositiveConflict();
        boolean triggered = properties.isEnabled()
                && ruleProperties.isEnabled()
                && isSad(baseResult.baseEmotionCode())
                && atLeast(voiceResult.confidence(), ruleProperties.getVoiceConfidenceMin())
                && below(fusedTextNeg, ruleProperties.getFusedTextNegMax())
                && !positiveHits.isEmpty()
                && negativeHits.isEmpty();

        String decisionCode = triggered
                ? LOW_CONSISTENCY_CODE
                : (hasText(baseResult.baseEmotionCode()) ? baseResult.baseEmotionCode() : UNKNOWN_CODE);
        String decisionStatus = triggered
                ? LOW_CONSISTENCY_STATUS
                : (hasText(baseResult.baseEmotionCode()) ? READY_STATUS : UNAVAILABLE_STATUS);
        String reason = triggered
                ? SAD_POSITIVE_CONFLICT_REASON
                : (hasText(baseResult.baseEmotionCode()) ? null : BASE_EMOTION_MISSING_REASON);
        String source = triggered ? CONSISTENCY_GUARD_SOURCE : BASE_RESULT_SOURCE;

        ConsistencyDecision.Audit audit = new ConsistencyDecision.Audit(
                SAD_POSITIVE_CONFLICT_RULE,
                triggered,
                taskId,
                audioId,
                traceId,
                baseResult.baseEmotionCode(),
                baseResult.baseConfidence(),
                voiceResult.label(),
                voiceResult.confidence(),
                fusionResult.label(),
                fusionResult.confidence(),
                round4(fusedTextNeg),
                textSentiment == null ? null : textSentiment.label(),
                textSentiment == null ? null : round4(textSentiment.negativeScore()),
                textSentiment == null ? null : round4(textSentiment.mappedMass()),
                textSentiment == null ? null : textSentiment.emotion4Ready(),
                positiveHits,
                negativeHits,
                buildTranscriptExcerpt(normalizedTranscript, positiveHitMatches, negativeHitMatches),
                new ConsistencyDecision.Thresholds(
                        round4(ruleProperties.getVoiceConfidenceMin()),
                        round4(ruleProperties.getFusedTextNegMax())
                )
        );

        ConsistencyDecision decision = new ConsistencyDecision(
                decisionCode,
                decisionStatus,
                reason,
                source,
                baseResult.baseEmotionCode(),
                baseResult.baseConfidence(),
                audit
        );
        logAudit(decision);
        return decision;
    }

    public ConsistencyDecision readPersistedDecision(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(rawJson).path("decision");
            if (node.isMissingNode() || node.isNull() || (node.isObject() && node.size() == 0)) {
                return null;
            }
            return objectMapper.treeToValue(node, ConsistencyDecision.class);
        } catch (Exception ex) {
            return null;
        }
    }

    public AnalysisTaskResultResponse.RiskAssessmentPayload applyDecisionNotice(
            AnalysisTaskResultResponse.RiskAssessmentPayload riskAssessment,
            ConsistencyDecision decision
    ) {
        if (riskAssessment == null) {
            return null;
        }
        String adviceText = riskAssessment.advice_text();
        if (decision != null && decision.isLowConsistency()) {
            adviceText = prependNotice(adviceText);
        }
        return new AnalysisTaskResultResponse.RiskAssessmentPayload(
                riskAssessment.risk_score(),
                riskAssessment.risk_level(),
                adviceText,
                riskAssessment.p_sad(),
                riskAssessment.p_angry(),
                riskAssessment.p_happy(),
                riskAssessment.var_conf(),
                riskAssessment.text_neg()
        );
    }

    private void logAudit(ConsistencyDecision decision) {
        if (!properties.isAuditLogEnabled() || decision == null || decision.audit() == null) {
            return;
        }
        try {
            String serialized = objectMapper.writeValueAsString(decision.audit());
            if (decision.audit().triggered()) {
                log.info("emotion_consistency_guard_evaluated {}", serialized);
            } else {
                log.debug("emotion_consistency_guard_evaluated {}", serialized);
            }
        } catch (Exception ex) {
            if (decision.audit().triggered()) {
                log.info("emotion_consistency_guard_evaluated rule={}, triggered={}, reason={}",
                        decision.audit().rule(),
                        decision.audit().triggered(),
                        decision.reason());
            } else {
                log.debug("emotion_consistency_guard_evaluated rule={}, triggered={}, reason={}",
                        decision.audit().rule(),
                        decision.audit().triggered(),
                        decision.reason());
            }
        }
    }

    private List<String> loadTerms(String resourcePath, List<String> defaults, String tag) {
        List<String> loaded = readUtf8Lines(resourcePath);
        if (loaded.isEmpty()) {
            log.warn("ConsistencyGuardService {} lexicon load failed, fallback to defaults. resource={}", tag, resourcePath);
            return List.copyOf(defaults);
        }
        log.info("ConsistencyGuardService {} lexicon loaded: resource={}, terms={}", tag, resourcePath, loaded.size());
        return List.copyOf(loaded);
    }

    private List<String> readUtf8Lines(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return Collections.emptyList();
        }
        InputStream stream = ConsistencyGuardService.class.getClassLoader().getResourceAsStream(resourcePath.trim());
        if (stream == null) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                lines.add(trimmed);
            }
        } catch (Exception ex) {
            return Collections.emptyList();
        }
        return dedupe(lines);
    }

    private String normalizeTranscript(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return "";
        }
        return WHITESPACE.matcher(transcript.replace('\r', ' ').replace('\n', ' ')).replaceAll(" ").trim();
    }

    private List<HitMatch> detectHits(String transcript, List<String> terms, List<HitMatch> blockedMatches) {
        if (transcript == null || transcript.isBlank() || terms == null || terms.isEmpty()) {
            return List.of();
        }
        String haystack = transcript.toLowerCase(Locale.ROOT);
        List<HitMatch> matches = new ArrayList<>();
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            String needle = term.toLowerCase(Locale.ROOT);
            int start = haystack.indexOf(needle);
            if (start < 0) {
                continue;
            }
            int end = start + needle.length();
            if (overlapsBlockedRange(start, end, blockedMatches)) {
                continue;
            }
            matches.add(new HitMatch(term, start, end));
        }
        matches.sort(Comparator.comparingInt(HitMatch::start));
        return matches;
    }

    private boolean overlapsBlockedRange(int start, int end, List<HitMatch> blockedMatches) {
        for (HitMatch blocked : blockedMatches) {
            if (start < blocked.end() && end > blocked.start()) {
                return true;
            }
        }
        return false;
    }

    private List<String> toHitTerms(List<HitMatch> matches) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (HitMatch match : matches) {
            ordered.add(match.term());
        }
        return List.copyOf(ordered);
    }

    private String buildTranscriptExcerpt(String transcript, List<HitMatch> positiveHits, List<HitMatch> negativeHits) {
        if (transcript == null || transcript.isBlank()) {
            return null;
        }
        AnalysisConsistencyProperties.SadPositiveConflict ruleProperties = properties.getSadPositiveConflict();
        int maxChars = Math.max(1, ruleProperties.getTranscriptExcerptMaxChars());
        List<HitMatch> anchors = new ArrayList<>();
        anchors.addAll(positiveHits);
        anchors.addAll(negativeHits);
        anchors.sort(Comparator.comparingInt(HitMatch::start));
        if (anchors.isEmpty()) {
            return transcript.substring(0, Math.min(maxChars, transcript.length()));
        }
        HitMatch anchor = anchors.get(0);
        int start = Math.max(0, anchor.start() - Math.max(0, ruleProperties.getTranscriptLeftContextChars()));
        int end = Math.min(transcript.length(), anchor.end() + Math.max(0, ruleProperties.getTranscriptRightContextChars()));
        if (end - start > maxChars) {
            end = Math.min(transcript.length(), start + maxChars);
            if (anchor.end() > end) {
                end = anchor.end();
                start = Math.max(0, end - maxChars);
            }
        }
        return transcript.substring(start, Math.min(end, transcript.length())).trim();
    }

    private BaseResult resolveBaseResult(SerAnalyzeResponse response) {
        if (response != null && response.fusion() != null && Boolean.TRUE.equals(response.fusion().ready()) && hasText(response.fusion().label())) {
            return new BaseResult(response.fusion().label(), response.fusion().confidence());
        }
        if (response != null && response.overall() != null && hasText(response.overall().emotionCode())) {
            return new BaseResult(response.overall().emotionCode(), response.overall().confidence());
        }
        return new BaseResult(null, null);
    }

    private VoiceResult resolveVoiceResult(SerAnalyzeResponse response) {
        if (response == null || response.overall() == null) {
            return new VoiceResult(null, null);
        }
        return new VoiceResult(response.overall().emotionCode(), response.overall().confidence());
    }

    private FusionResult resolveFusionResult(SerAnalyzeResponse response) {
        if (response == null || response.fusion() == null) {
            return new FusionResult(null, null);
        }
        return new FusionResult(response.fusion().label(), response.fusion().confidence());
    }

    private boolean isSad(String emotionCode) {
        if (!hasText(emotionCode)) {
            return false;
        }
        String normalized = emotionCode.trim().toUpperCase(Locale.ROOT);
        return "SAD".equals(normalized) || "SADNESS".equals(normalized);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean atLeast(Double value, double threshold) {
        return value != null && value >= threshold;
    }

    private boolean below(double value, double threshold) {
        return value < threshold;
    }

    private String prependNotice(String adviceText) {
        if (adviceText == null || adviceText.isBlank()) {
            return LOW_CONSISTENCY_NOTICE;
        }
        if (adviceText.startsWith(LOW_CONSISTENCY_NOTICE)) {
            return adviceText;
        }
        return LOW_CONSISTENCY_NOTICE + "；" + adviceText;
    }

    private Double round4(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 10000.0D) / 10000.0D;
    }

    private List<String> dedupe(List<String> items) {
        return new ArrayList<>(new LinkedHashSet<>(items));
    }

    private record HitMatch(String term, int start, int end) {
    }

    private record BaseResult(String baseEmotionCode, Double baseConfidence) {
    }

    private record VoiceResult(String label, Double confidence) {
    }

    private record FusionResult(String label, Double confidence) {
    }
}
