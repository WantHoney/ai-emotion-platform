package com.wuhao.aiemotion.service;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TextNegScorer {

    private static final List<String> NEGATIVE_TERMS = List.of(
            "难受", "抑郁", "想哭", "崩溃", "压力", "焦虑", "失眠", "绝望", "没意义", "不想活",
            "烦", "恐惧", "害怕", "孤独", "无助", "自责", "内疚"
    );
    private static final List<String> HIGH_RISK_TERMS = List.of("不想活", "轻生", "自杀");

    public TextNegScoreResult score(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return new TextNegScoreResult(0.0D, 0, List.of(), false);
        }

        int hitCount = 0;
        List<String> hits = new java.util.ArrayList<>();
        for (String term : NEGATIVE_TERMS) {
            int count = occurrences(transcript, term);
            if (count > 0) {
                hitCount += count;
                hits.add(term + "x" + count);
            }
        }

        boolean highRiskHit = HIGH_RISK_TERMS.stream().anyMatch(transcript::contains);
        double textNeg = clamp(hitCount / 8.0D, 0.0D, 1.0D);
        if (highRiskHit) {
            textNeg = Math.max(textNeg, 0.8D);
        }

        return new TextNegScoreResult(textNeg, hitCount, List.copyOf(hits), highRiskHit);
    }

    private int occurrences(String text, String term) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(term, idx)) != -1) {
            count++;
            idx += term.length();
        }
        return count;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record TextNegScoreResult(double textNeg, int hitCount, List<String> hits, boolean highRiskHit) {
    }
}

