package com.wuhao.aiemotion.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextNegScorerTest {

    private final TextNegScorer scorer = new TextNegScorer();

    @Test
    void shouldApplyDegreeRule() {
        TextNegScorer.TextNegScoreResult strong = scorer.score("我非常焦虑，压力很大");
        TextNegScorer.TextNegScoreResult mild = scorer.score("我有点焦虑");

        assertTrue(strong.textNeg() > mild.textNeg());
    }

    @Test
    void shouldReduceScoreWhenNegated() {
        TextNegScorer.TextNegScoreResult negated = scorer.score("我不太焦虑");
        TextNegScorer.TextNegScoreResult plain = scorer.score("我焦虑");

        assertFalse(negated.highRiskHit());
        assertTrue(negated.textNeg() < plain.textNeg());
    }

    @Test
    void shouldRaiseScoreForHighRiskTerms() {
        TextNegScorer.TextNegScoreResult result = scorer.score("我不想活了");

        assertTrue(result.highRiskHit());
        assertTrue(result.textNeg() >= 0.8D);
    }

    @Test
    void shouldReturnZeroForEmptyText() {
        TextNegScorer.TextNegScoreResult result = scorer.score(" ");

        assertEquals(0.0D, result.textNeg());
        assertEquals(0, result.hitCount());
        assertFalse(result.highRiskHit());
    }
}
