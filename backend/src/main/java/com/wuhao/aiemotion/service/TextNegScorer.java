package com.wuhao.aiemotion.service;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class TextNegScorer {

    private static final double NORMALIZER = 10.0D;
    private static final double HIGH_RISK_FLOOR = 0.8D;
    private static final double NEGATION_REDUCTION = 0.30D;
    private static final int NEGATION_LEFT_CONTEXT = 8;
    private static final int DEGREE_LEFT_CONTEXT = 8;
    private static final String LEXICON_ROOT = "lexicon/";

    private static final Map<String, Double> NEGATIVE_TERM_WEIGHTS = buildNegativeTermWeights();
    private static final List<String> HIGH_RISK_TERMS = buildHighRiskTerms();
    private static final List<String> NEGATION_TERMS = buildNegationTerms();
    private static final Map<String, Double> DEGREE_TERMS = buildDegreeTerms();

    public TextNegScoreResult score(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return new TextNegScoreResult(0.0D, 0, List.of(), false);
        }

        String normalized = normalize(transcript);
        double weightedHits = 0.0D;
        int hitCount = 0;
        List<String> hits = new ArrayList<>();

        for (Map.Entry<String, Double> entry : NEGATIVE_TERM_WEIGHTS.entrySet()) {
            String term = entry.getKey();
            List<Integer> positions = allIndexesOf(normalized, term);
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
            hits.add(term + "x" + positions.size());
        }

        boolean highRiskHit = HIGH_RISK_TERMS.stream().anyMatch(normalized::contains);
        double textNeg = clamp(weightedHits / NORMALIZER, 0.0D, 1.0D);
        if (highRiskHit) {
            textNeg = Math.max(textNeg, HIGH_RISK_FLOOR);
        }

        return new TextNegScoreResult(round4(textNeg), hitCount, List.copyOf(hits), highRiskHit);
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

        LinkedHashMap<String, Double> loaded = new LinkedHashMap<>();
        loaded.putAll(readWeightedLexicon(LEXICON_ROOT + "text_neg_zh.txt"));
        loaded.putAll(readWeightedLexicon(LEXICON_ROOT + "text_neg_en.txt"));
        if (loaded.isEmpty()) {
            loaded.putAll(defaults);
        }
        return Map.copyOf(loaded);
    }

    private static List<String> buildHighRiskTerms() {
        List<String> defaults = List.of(
                "不想活", "轻生", "自杀", "结束生命", "活不下去",
                "kill myself", "suicide", "end my life"
        );
        List<String> loaded = new ArrayList<>();
        loaded.addAll(readTermLexicon(LEXICON_ROOT + "high_risk_zh.txt"));
        loaded.addAll(readTermLexicon(LEXICON_ROOT + "high_risk_en.txt"));
        return loaded.isEmpty() ? defaults : List.copyOf(loaded);
    }

    private static List<String> buildNegationTerms() {
        List<String> defaults = List.of(
                "不", "没", "没有", "无", "并不", "并非", "不太", "不是", "未"
        );
        List<String> loaded = readTermLexicon(LEXICON_ROOT + "negation_terms_zh.txt");
        return loaded.isEmpty() ? defaults : List.copyOf(loaded);
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
        return loaded.isEmpty() ? defaults : Map.copyOf(loaded);
    }

    private static Map<String, Double> readWeightedLexicon(String resourcePath) {
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (String line : readUtf8Lines(resourcePath)) {
            String[] fields = line.split("[,\t]", 2);
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
            String[] fields = line.split("[,\t]", 2);
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
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
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

    private List<Integer> allIndexesOf(String text, String term) {
        List<Integer> indexes = new ArrayList<>();
        int idx = 0;
        while ((idx = text.indexOf(term, idx)) != -1) {
            indexes.add(idx);
            idx += Math.max(1, term.length());
        }
        return indexes;
    }

    private boolean isNegated(String text, int start) {
        int leftStart = Math.max(0, start - NEGATION_LEFT_CONTEXT);
        String left = text.substring(leftStart, start);
        for (String neg : NEGATION_TERMS) {
            if (left.contains(neg)) {
                return true;
            }
        }
        return false;
    }

    private double degreeMultiplier(String text, int start) {
        int leftStart = Math.max(0, start - DEGREE_LEFT_CONTEXT);
        String left = text.substring(leftStart, start);
        double factor = 1.0D;
        for (Map.Entry<String, Double> degree : DEGREE_TERMS.entrySet()) {
            if (left.contains(degree.getKey())) {
                factor = Math.max(factor, degree.getValue());
            }
        }
        return factor;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round4(double value) {
        return Math.round(value * 10000.0D) / 10000.0D;
    }

    public record TextNegScoreResult(double textNeg, int hitCount, List<String> hits, boolean highRiskHit) {
    }
}
