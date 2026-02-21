package com.wuhao.aiemotion.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextNegScorerTest {

    private final TextNegScorer scorer = new TextNegScorer();

    @Test
    void shouldScoreNegativeSignals() {
        TextNegScorer.TextNegScoreResult result = scorer.score("\u538b\u529b\u5f88\u5927\uff0c\u7126\u8651\u5931\u7720\uff0c\u771f\u7684\u5f88\u65e0\u52a9");

        assertTrue(result.hitCount() >= 3);
        assertTrue(result.textNeg() > 0.30D);
        assertTrue(result.hits().stream().anyMatch(h -> h.contains("\u538b\u529b") || h.contains("\u7126\u8651")));
    }

    @Test
    void shouldReduceScoreWhenNegated() {
        TextNegScorer.TextNegScoreResult result = scorer.score("\u6211\u4e0d\u592a\u7126\u8651\uff0c\u53ea\u662f\u6709\u70b9\u62c5\u5fc3");

        assertFalse(result.highRiskHit());
        assertTrue(result.textNeg() < 0.25D);
    }

    @Test
    void shouldRaiseScoreForHighRiskTerms() {
        TextNegScorer.TextNegScoreResult result = scorer.score("\u6211\u6709\u70b9\u70e6\uff0c\u751a\u81f3\u4e0d\u60f3\u6d3b\u4e86");

        assertTrue(result.highRiskHit());
        assertTrue(result.textNeg() >= 0.8D);
    }
}
