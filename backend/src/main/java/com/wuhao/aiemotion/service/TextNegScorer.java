package com.wuhao.aiemotion.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class TextNegScorer {

    private static final double NORMALIZER = 10.0D;
    private static final double HIGH_RISK_FLOOR = 0.8D;
    private static final double NEGATION_REDUCTION = 0.30D;
    private static final int LEFT_CONTEXT = 4;

    private static final Map<String, Double> NEGATIVE_TERM_WEIGHTS = buildNegativeTermWeights();

    private static final List<String> HIGH_RISK_TERMS = List.of(
            "\u4e0d\u60f3\u6d3b", "\u8f7b\u751f", "\u81ea\u6740", "\u7ed3\u675f\u751f\u547d", "\u6d3b\u4e0d\u4e0b\u53bb",
            "kill myself", "suicide", "end my life"
    );

    private static final List<String> NEGATION_TERMS = List.of(
            "\u4e0d", "\u6ca1", "\u6ca1\u6709", "\u65e0", "\u5e76\u4e0d", "\u5e76\u975e", "\u4e0d\u592a", "\u4e0d\u662f", "\u672a"
    );

    private static final Map<String, Double> DEGREE_TERMS = Map.ofEntries(
            Map.entry("\u975e\u5e38", 1.8D),
            Map.entry("\u7279\u522b", 1.6D),
            Map.entry("\u6781\u5176", 2.0D),
            Map.entry("\u592a", 1.4D),
            Map.entry("\u5f88", 1.2D),
            Map.entry("\u633a", 1.2D),
            Map.entry("\u6709\u70b9", 0.8D),
            Map.entry("\u6709\u4e9b", 0.85D),
            Map.entry("\u7a0d\u5fae", 0.7D),
            Map.entry("\u7565\u5fae", 0.7D)
    );

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
        Map<String, Double> terms = new LinkedHashMap<>();

        // medium
        terms.put("\u96be\u53d7", 1.0D);
        terms.put("\u96be\u8fc7", 1.0D);
        terms.put("\u60f3\u54ed", 1.0D);
        terms.put("\u538b\u529b", 1.0D);
        terms.put("\u7126\u8651", 1.1D);
        terms.put("\u7d27\u5f20", 0.9D);
        terms.put("\u5931\u7720", 1.0D);
        terms.put("\u5b64\u72ec", 1.0D);
        terms.put("\u65e0\u52a9", 1.0D);
        terms.put("\u5fc3\u7d2f", 0.9D);
        terms.put("\u75b2\u60eb", 0.9D);
        terms.put("\u60f3\u8e72", 1.0D);
        terms.put("\u538b\u6291", 1.2D);
        terms.put("\u5fc3\u614c", 1.1D);
        terms.put("\u6050\u60e7", 1.1D);
        terms.put("\u5bb3\u6015", 1.0D);
        terms.put("\u62c5\u5fc3", 0.8D);
        terms.put("\u70e6", 0.8D);
        terms.put("\u70e6\u8e81", 1.0D);
        terms.put("\u5185\u8017", 0.9D);
        terms.put("\u5d29\u4e86", 1.2D);

        // severe
        terms.put("\u5d29\u6e83", 1.6D);
        terms.put("\u5d29\u6ebb", 1.6D);
        terms.put("\u7edd\u671b", 1.8D);
        terms.put("\u6ca1\u610f\u4e49", 1.6D);
        terms.put("\u65e0\u671b", 1.5D);
        terms.put("\u65e0\u529b", 1.2D);
        terms.put("\u81ea\u8d23", 1.0D);
        terms.put("\u5185\u759a", 1.0D);
        terms.put("\u75db\u82e6", 1.2D);

        // english fallback
        terms.put("depressed", 1.4D);
        terms.put("anxious", 1.2D);
        terms.put("panic", 1.3D);
        terms.put("hopeless", 1.6D);
        terms.put("lonely", 1.0D);
        terms.put("helpless", 1.0D);
        terms.put("insomnia", 1.0D);
        terms.put("stressed", 1.0D);
        terms.put("burnout", 1.2D);
        terms.put("overwhelmed", 1.1D);

        return Map.copyOf(terms);
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
        int leftStart = Math.max(0, start - LEFT_CONTEXT);
        String left = text.substring(leftStart, start);
        for (String neg : NEGATION_TERMS) {
            if (left.contains(neg)) {
                return true;
            }
        }
        return false;
    }

    private double degreeMultiplier(String text, int start) {
        int leftStart = Math.max(0, start - LEFT_CONTEXT);
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
