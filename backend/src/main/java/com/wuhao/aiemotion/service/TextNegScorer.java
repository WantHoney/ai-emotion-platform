package com.wuhao.aiemotion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TextNegScorer {

    private static final Logger log = LoggerFactory.getLogger(TextNegScorer.class);

    private static final double DEFAULT_NORMALIZER = 10.0D;
    private static final double DEFAULT_HIGH_RISK_FLOOR = 0.8D;
    private static final double NEGATION_REDUCTION = 0.30D;
    private static final int NEGATION_LEFT_CONTEXT = 8;
    private static final int DEGREE_LEFT_CONTEXT = 8;
    private static final int NEGATION_DISTANCE_LIMIT = 4;
    private static final int DEGREE_DISTANCE_LIMIT = 6;
    private static final String LEXICON_ROOT = "lexicon/";
    private static final String CLAUSE_DELIMITERS = ",.;!?，。；！？\n\r";

    private static final Map<String, Pattern> ENGLISH_PATTERN_CACHE = new ConcurrentHashMap<>();

    private static final Map<String, Double> NEGATIVE_TERM_WEIGHTS = buildNegativeTermWeights();
    private static final List<String> POSITIVE_TERMS = buildPositiveTerms();
    private static final List<String> ANGER_TERMS = buildAngerTerms();
    private static final List<String> SAD_TERMS = buildSadTerms();
    private static final List<String> NEUTRAL_CUE_TERMS = buildNeutralCueTerms();
    private static final List<String> HIGH_RISK_TERMS = buildHighRiskTerms();
    private static final List<String> NEGATION_TERMS = buildNegationTerms();
    private static final Map<String, Double> DEGREE_TERMS = buildDegreeTerms();
    private static final Map<String, String> DIAGNOSTIC_TERM_GROUPS = buildDiagnosticTermGroups();

    private final double normalizer;
    private final double highRiskFloor;

    public TextNegScorer(
            @Value("${text.neg.normalizer:10.0}") double normalizer,
            @Value("${text.neg.high-risk-floor:0.8}") double highRiskFloor
    ) {
        this.normalizer = normalizer > 0.0D ? normalizer : DEFAULT_NORMALIZER;
        this.highRiskFloor = Double.isFinite(highRiskFloor)
                ? clamp(highRiskFloor, 0.0D, 1.0D)
                : DEFAULT_HIGH_RISK_FLOOR;
    }

    public TextNegScoreResult score(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return new TextNegScoreResult(0.0D, 0, List.of(), false, "NEU", 0.0D, defaultEmotion4Scores(), 0);
        }

        String normalized = normalize(transcript);
        double weightedHits = 0.0D;
        int hitCount = 0;

        for (Map.Entry<String, Double> entry : NEGATIVE_TERM_WEIGHTS.entrySet()) {
            String term = entry.getKey();
            List<Integer> positions = findTermPositions(normalized, term);
            if (positions.isEmpty()) {
                continue;
            }

            hitCount += positions.size();
            double termScore = 0.0D;
            for (int start : positions) {
                double degree = degreeMultiplier(normalized, start);
                boolean negated = isNegated(normalized, start);
                double local = entry.getValue() * degree * (negated ? NEGATION_REDUCTION : 1.0D);
                termScore += local;
            }
            weightedHits += termScore;
        }

        DiagnosticSummary diagnostic = buildDiagnosticSummary(normalized);
        boolean highRiskHit = hasAnyTerm(normalized, HIGH_RISK_TERMS);
        double textNeg = clamp(weightedHits / normalizer, 0.0D, 1.0D);
        if (highRiskHit) {
            textNeg = Math.max(textNeg, highRiskFloor);
        }

        return new TextNegScoreResult(
                round4(textNeg),
                hitCount,
                diagnostic.hits(),
                highRiskHit,
                diagnostic.dominantEmotion(),
                round4(diagnostic.diagnosticScore()),
                diagnostic.emotion4Scores(),
                diagnostic.hitCount()
        );
    }

    private static Map<String, Double> buildNegativeTermWeights() {
        LinkedHashMap<String, Double> defaults = new LinkedHashMap<>();

        defaults.put("难受", 1.0D);
        defaults.put("难过", 1.0D);
        defaults.put("压力", 1.0D);
        defaults.put("焦虑", 1.1D);
        defaults.put("紧张", 0.9D);
        defaults.put("恐惧", 1.1D);
        defaults.put("害怕", 1.0D);
        defaults.put("担心", 0.8D);
        defaults.put("烦躁", 1.0D);
        defaults.put("崩溃", 1.6D);
        defaults.put("绝望", 1.8D);
        defaults.put("抑郁", 1.2D);
        defaults.put("崩了", 1.2D);
        defaults.put("孤独", 1.0D);
        defaults.put("无助", 1.0D);
        defaults.put("痛苦", 1.2D);
        defaults.put("失眠", 1.0D);
        defaults.put("depressed", 1.4D);
        defaults.put("anxious", 1.2D);
        defaults.put("panic", 1.3D);
        defaults.put("hopeless", 1.6D);
        defaults.put("stressed", 1.0D);

        Map<String, Double> zh = readWeightedLexicon(LEXICON_ROOT + "text_neg_zh.txt");
        Map<String, Double> en = readWeightedLexicon(LEXICON_ROOT + "text_neg_en.txt");

        LinkedHashMap<String, Double> loaded = new LinkedHashMap<>();
        loaded.putAll(zh);
        loaded.putAll(en);

        if (loaded.isEmpty()) {
            log.warn("TextNegScorer lexicon load failed, fallback to defaults. normalizer={}", DEFAULT_NORMALIZER);
            loaded.putAll(defaults);
        }

        log.info("TextNegScorer negative lexicon loaded: zhTerms={}, enTerms={}, activeTerms={}",
                zh.size(), en.size(), loaded.size());
        return Map.copyOf(loaded);
    }

    private static List<String> buildPositiveTerms() {
        List<String> defaults = List.of(
                "开心", "高兴", "快乐", "愉快", "喜悦", "幸福", "真好", "很好", "挺好", "不错",
                "舒服", "放松", "满足", "喜欢", "心情很好", "太阳非常好", "散步", "休息得很好",
                "休息的很好", "开心极了", "很开心", "超开心", "蛮开心", "轻松", "顺利"
        );

        List<String> loaded = readTermLexicon(LEXICON_ROOT + "text_positive_zh.txt");
        if (loaded.isEmpty()) {
            log.warn("TextNegScorer positive lexicon load failed, fallback to defaults.");
            loaded = defaults;
        }
        log.info("TextNegScorer positive lexicon loaded: {}", loaded.size());
        return List.copyOf(loaded);
    }

    private static List<String> buildAngerTerms() {
        List<String> defaults = List.of(
                "愤怒", "生气", "气愤", "恼火", "火大", "暴躁", "发火", "气死", "烦死", "怒"
        );

        List<String> loaded = readTermLexicon(LEXICON_ROOT + "text_anger_zh.txt");
        if (loaded.isEmpty()) {
            log.warn("TextNegScorer anger lexicon load failed, fallback to defaults.");
            loaded = defaults;
        }
        log.info("TextNegScorer anger lexicon loaded: {}", loaded.size());
        return List.copyOf(loaded);
    }

    private static List<String> buildSadTerms() {
        List<String> defaults = List.of(
                "难过", "伤心", "悲伤", "想哭", "沮丧", "低落", "失落", "难受", "委屈", "郁闷",
                "不开心", "心酸", "糟糕"
        );

        List<String> loaded = readTermLexicon(LEXICON_ROOT + "text_sad_zh.txt");
        if (loaded.isEmpty()) {
            log.warn("TextNegScorer sad lexicon load failed, fallback to defaults.");
            loaded = defaults;
        }
        log.info("TextNegScorer sad lexicon loaded: {}", loaded.size());
        return List.copyOf(loaded);
    }

    private static List<String> buildNeutralCueTerms() {
        List<String> defaults = List.of(
                "是什么情绪", "什么情绪", "能听见", "测试", "录音", "请问", "嗯", "啊"
        );

        List<String> loaded = readTermLexicon(LEXICON_ROOT + "text_neutral_cues_zh.txt");
        if (loaded.isEmpty()) {
            log.warn("TextNegScorer neutral cue lexicon load failed, fallback to defaults.");
            loaded = defaults;
        }
        log.info("TextNegScorer neutral cue lexicon loaded: {}", loaded.size());
        return List.copyOf(loaded);
    }

    private static List<String> buildHighRiskTerms() {
        List<String> defaults = List.of(
                "不想活", "轻生", "自杀", "结束生命", "活不下去",
                "kill myself", "suicide", "end my life"
        );

        List<String> zh = readTermLexicon(LEXICON_ROOT + "high_risk_zh.txt");
        List<String> en = readTermLexicon(LEXICON_ROOT + "high_risk_en.txt");

        List<String> loaded = new ArrayList<>();
        loaded.addAll(zh);
        loaded.addAll(en);

        if (loaded.isEmpty()) {
            log.warn("TextNegScorer high-risk lexicon load failed, fallback to defaults.");
            loaded = new ArrayList<>(defaults);
        }

        log.info("TextNegScorer high-risk lexicon loaded: zhTerms={}, enTerms={}, activeTerms={}",
                zh.size(), en.size(), loaded.size());
        return List.copyOf(loaded);
    }

    private static List<String> buildNegationTerms() {
        List<String> defaults = List.of(
                "并不是", "并没有", "不怎么", "没有那么", "不太", "不是", "并不", "并非", "没有", "未", "无", "没", "不"
        );

        List<String> loaded = readTermLexicon(LEXICON_ROOT + "negation_terms_zh.txt");
        if (loaded.isEmpty()) {
            log.warn("TextNegScorer negation lexicon load failed, fallback to defaults.");
            loaded = defaults;
        }

        log.info("TextNegScorer negation terms loaded: {}", loaded.size());
        return List.copyOf(loaded);
    }

    private static Map<String, Double> buildDegreeTerms() {
        Map<String, Double> defaults = Map.ofEntries(
                Map.entry("非常", 1.8D),
                Map.entry("特别", 1.6D),
                Map.entry("极其", 2.0D),
                Map.entry("十分", 1.6D),
                Map.entry("太", 1.4D),
                Map.entry("很", 1.2D),
                Map.entry("挺", 1.2D),
                Map.entry("有点", 0.8D),
                Map.entry("有些", 0.85D),
                Map.entry("稍微", 0.7D),
                Map.entry("略微", 0.7D)
        );

        Map<String, Double> loaded = readDegreeLexicon(LEXICON_ROOT + "degree_terms_zh.csv");
        if (loaded.isEmpty()) {
            log.warn("TextNegScorer degree lexicon load failed, fallback to defaults.");
            loaded = defaults;
        }

        log.info("TextNegScorer degree terms loaded: {}", loaded.size());
        return Map.copyOf(loaded);
    }

    private static Map<String, Double> readWeightedLexicon(String resourcePath) {
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (String line : readUtf8Lines(resourcePath)) {
            String[] fields = line.split("[,\\t]", 2);
            if (fields.length != 2) {
                continue;
            }
            String term = fields[0].trim().toLowerCase(Locale.ROOT);
            try {
                double weight = Double.parseDouble(fields[1].trim());
                if (!term.isEmpty() && weight > 0.0D) {
                    result.put(term, weight);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private static List<String> readTermLexicon(String resourcePath) {
        List<String> result = new ArrayList<>();
        for (String line : readUtf8Lines(resourcePath)) {
            String term = line.trim().toLowerCase(Locale.ROOT);
            if (!term.isEmpty()) {
                result.add(term);
            }
        }
        return result;
    }

    private static Map<String, Double> readDegreeLexicon(String resourcePath) {
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (String line : readUtf8Lines(resourcePath)) {
            String[] fields = line.split("[,\\t]", 2);
            if (fields.length != 2) {
                continue;
            }
            String term = fields[0].trim().toLowerCase(Locale.ROOT);
            try {
                double factor = Double.parseDouble(fields[1].trim());
                if (!term.isEmpty() && factor > 0.0D) {
                    result.put(term, factor);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private static List<String> readUtf8Lines(String resourcePath) {
        InputStream stream = TextNegScorer.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            log.warn("TextNegScorer resource not found: {}", resourcePath);
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
            return lines;
        } catch (Exception ex) {
            log.warn("TextNegScorer failed to read resource: {}", resourcePath, ex);
            return Collections.emptyList();
        }
    }

    private static Map<String, String> buildDiagnosticTermGroups() {
        LinkedHashMap<String, String> groups = new LinkedHashMap<>();
        NEGATIVE_TERM_WEIGHTS.keySet().forEach(term -> groups.put(term, "NEG"));
        SAD_TERMS.forEach(term -> groups.put(term, "SAD"));
        ANGER_TERMS.forEach(term -> groups.put(term, "ANG"));
        POSITIVE_TERMS.forEach(term -> groups.put(term, "HAP"));
        return Map.copyOf(groups);
    }

    private String normalize(String text) {
        return text
                .toLowerCase(Locale.ROOT)
                .replace('\u3000', ' ')
                .replace("\uFF0C", ",")
                .replace("\u3002", ".")
                .replace("\uFF1B", ";")
                .replace("\uFF01", "!")
                .replace("\uFF1F", "?")
                .trim();
    }

    private boolean hasAnyTerm(String text, List<String> terms) {
        for (String term : terms) {
            if (!findTermPositions(text, term).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<Integer> findTermPositions(String text, String term) {
        if (isEnglishTerm(term)) {
            return englishWordBoundaryIndexes(text, term);
        }
        return substringIndexes(text, term);
    }

    private List<Integer> substringIndexes(String text, String term) {
        List<Integer> indexes = new ArrayList<>();
        int idx = 0;
        while ((idx = text.indexOf(term, idx)) != -1) {
            indexes.add(idx);
            idx += Math.max(1, term.length());
        }
        return indexes;
    }

    private List<Integer> englishWordBoundaryIndexes(String text, String term) {
        Pattern pattern = ENGLISH_PATTERN_CACHE.computeIfAbsent(term, t ->
                Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(t) + "(?![a-z0-9_])")
        );
        Matcher matcher = pattern.matcher(text);
        List<Integer> indexes = new ArrayList<>();
        while (matcher.find()) {
            indexes.add(matcher.start());
        }
        return indexes;
    }

    private boolean isEnglishTerm(String term) {
        return term != null && !term.isBlank() && term.matches("[a-z0-9 ]+");
    }

    private DiagnosticSummary buildDiagnosticSummary(String text) {
        List<DiagnosticHit> hits = collectNonOverlappingDiagnosticHits(text);
        List<DiagnosticHit> neutralHits = collectNeutralCueHits(text);

        int positiveCount = countGroupHits(hits, "HAP");
        int angerCount = countGroupHits(hits, "ANG");
        int sadCount = countGroupHits(hits, "SAD");
        int negativeCount = countGroupHits(hits, "NEG");
        int neutralCueCount = neutralHits.size();

        double positiveRaw = positiveCount;
        double angerRaw = angerCount;
        double sadRaw = sadCount;
        double negativeRaw = negativeCount;

        Map<String, Double> emotion4Scores = normalizeEmotion4Scores(Map.of(
                "ANG", angerRaw + negativeRaw * 0.35D,
                "HAP", positiveRaw,
                "NEU", (positiveRaw + angerRaw + sadRaw + negativeRaw) <= 0.0D
                        ? 1.2D
                        : (neutralCueCount > 0 ? 0.45D : 0.2D),
                "SAD", sadRaw + negativeRaw * 0.55D
        ));

        String dominantEmotion = maxEmotionLabel(emotion4Scores);
        double evidence = positiveRaw + angerRaw + sadRaw + negativeRaw + neutralCueCount * 0.5D;
        double diagnosticScore = clamp(evidence / 3.0D, 0.0D, 1.0D);

        List<String> aggregatedHits = aggregateDiagnosticHits(hits, neutralHits);
        return new DiagnosticSummary(
                dominantEmotion,
                diagnosticScore,
                emotion4Scores,
                aggregatedHits,
                hits.size() + neutralHits.size()
        );
    }

    private List<DiagnosticHit> collectNonOverlappingDiagnosticHits(String text) {
        List<Map.Entry<String, String>> candidates = DIAGNOSTIC_TERM_GROUPS.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, String>>comparingInt(entry -> entry.getKey().length())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();

        boolean[] occupied = new boolean[Math.max(1, text.length())];
        List<DiagnosticHit> hits = new ArrayList<>();
        for (Map.Entry<String, String> entry : candidates) {
            String term = entry.getKey();
            for (int start : findTermPositions(text, term)) {
                int end = Math.min(text.length(), start + term.length());
                if (isOccupied(occupied, start, end)) {
                    continue;
                }
                markOccupied(occupied, start, end);
                hits.add(new DiagnosticHit(term, entry.getValue(), start, end));
            }
        }
        hits.sort(Comparator.comparingInt(DiagnosticHit::start));
        return hits;
    }

    private List<DiagnosticHit> collectNeutralCueHits(String text) {
        List<DiagnosticHit> hits = new ArrayList<>();
        for (String term : NEUTRAL_CUE_TERMS) {
            for (int start : findTermPositions(text, term)) {
                hits.add(new DiagnosticHit(term, "NEU", start, Math.min(text.length(), start + term.length())));
            }
        }
        hits.sort(Comparator.comparingInt(DiagnosticHit::start));
        return hits;
    }

    private boolean isOccupied(boolean[] occupied, int start, int end) {
        for (int index = start; index < end && index < occupied.length; index++) {
            if (occupied[index]) {
                return true;
            }
        }
        return false;
    }

    private void markOccupied(boolean[] occupied, int start, int end) {
        for (int index = start; index < end && index < occupied.length; index++) {
            occupied[index] = true;
        }
    }

    private int countGroupHits(List<DiagnosticHit> hits, String group) {
        int count = 0;
        for (DiagnosticHit hit : hits) {
            if (group.equals(hit.group())) {
                count++;
            }
        }
        return count;
    }

    private List<String> aggregateDiagnosticHits(List<DiagnosticHit> hits, List<DiagnosticHit> neutralHits) {
        LinkedHashMap<String, Integer> counters = new LinkedHashMap<>();
        for (DiagnosticHit hit : hits) {
            counters.merge(hit.group() + ":" + hit.term(), 1, Integer::sum);
        }
        for (DiagnosticHit hit : neutralHits) {
            counters.merge(hit.group() + ":" + hit.term(), 1, Integer::sum);
        }

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counters.entrySet()) {
            result.add(entry.getKey() + "x" + entry.getValue());
        }
        return List.copyOf(result);
    }

    private Map<String, Double> normalizeEmotion4Scores(Map<String, Double> rawScores) {
        LinkedHashMap<String, Double> normalized = new LinkedHashMap<>();
        double total = 0.0D;
        for (String label : List.of("ANG", "HAP", "NEU", "SAD")) {
            double value = Math.max(0.0D, rawScores.getOrDefault(label, 0.0D));
            normalized.put(label, value);
            total += value;
        }
        if (total <= 0.0D) {
            return defaultEmotion4Scores();
        }
        for (String label : normalized.keySet()) {
            normalized.put(label, round4(normalized.get(label) / total));
        }
        return Map.copyOf(normalized);
    }

    private Map<String, Double> defaultEmotion4Scores() {
        return Map.of(
                "ANG", 0.0D,
                "HAP", 0.0D,
                "NEU", 1.0D,
                "SAD", 0.0D
        );
    }

    private String maxEmotionLabel(Map<String, Double> scores) {
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NEU");
    }

    private boolean isNegated(String text, int start) {
        String clause = recentClause(text, start, NEGATION_LEFT_CONTEXT);
        if (clause.isEmpty()) {
            return false;
        }

        int bestEnd = -1;
        int bestLength = 0;
        for (String neg : NEGATION_TERMS) {
            int idx = clause.lastIndexOf(neg);
            if (idx < 0) {
                continue;
            }
            int end = idx + neg.length();
            if (end > bestEnd || (end == bestEnd && neg.length() > bestLength)) {
                bestEnd = end;
                bestLength = neg.length();
            }
        }

        if (bestEnd < 0) {
            return false;
        }

        int distance = clause.length() - bestEnd;
        int limit = bestLength >= 2 ? NEGATION_DISTANCE_LIMIT + 2 : NEGATION_DISTANCE_LIMIT;
        return distance <= limit;
    }

    private double degreeMultiplier(String text, int start) {
        String clause = recentClause(text, start, DEGREE_LEFT_CONTEXT);
        if (clause.isEmpty()) {
            return 1.0D;
        }

        double factor = 1.0D;
        int bestDistance = Integer.MAX_VALUE;

        for (Map.Entry<String, Double> degree : DEGREE_TERMS.entrySet()) {
            int idx = clause.lastIndexOf(degree.getKey());
            if (idx < 0) {
                continue;
            }
            int distance = clause.length() - (idx + degree.getKey().length());
            if (distance > DEGREE_DISTANCE_LIMIT) {
                continue;
            }
            if (degree.getValue() > factor || (Double.compare(degree.getValue(), factor) == 0 && distance < bestDistance)) {
                factor = degree.getValue();
                bestDistance = distance;
            }
        }
        return factor;
    }

    private String recentClause(String text, int start, int leftWindow) {
        int leftStart = Math.max(0, start - leftWindow);
        String left = text.substring(leftStart, start);
        int cut = -1;
        for (int i = 0; i < CLAUSE_DELIMITERS.length(); i++) {
            char delimiter = CLAUSE_DELIMITERS.charAt(i);
            int idx = left.lastIndexOf(delimiter);
            if (idx > cut) {
                cut = idx;
            }
        }
        if (cut >= 0 && cut + 1 < left.length()) {
            left = left.substring(cut + 1);
        }
        return left.trim();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round4(double value) {
        return Math.round(value * 10000.0D) / 10000.0D;
    }

    private record DiagnosticHit(String term, String group, int start, int end) {
    }

    private record DiagnosticSummary(
            String dominantEmotion,
            double diagnosticScore,
            Map<String, Double> emotion4Scores,
            List<String> hits,
            int hitCount
    ) {
    }

    public record TextNegScoreResult(
            double textNeg,
            int hitCount,
            List<String> hits,
            boolean highRiskHit,
            String dominantEmotion,
            double diagnosticScore,
            Map<String, Double> emotion4Scores,
            int diagnosticHitCount
    ) {
    }
}
