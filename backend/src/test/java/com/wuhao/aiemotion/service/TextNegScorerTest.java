package com.wuhao.aiemotion.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextNegScorerTest {

    private final TextNegScorer scorer = new TextNegScorer();

    @Test
    void shouldCountHitsAndClamp() {
        TextNegScorer.TextNegScoreResult result = scorer.score("压力很大，焦虑失眠，真的很无助");

        assertEquals(4, result.hitCount());
        assertEquals(0.5, result.textNeg());
        assertTrue(result.hits().contains("压力x1"));
    }

    @Test
    void shouldRaiseScoreForHighRiskTerms() {
        TextNegScorer.TextNegScoreResult result = scorer.score("我有点烦，甚至不想活了");

        assertTrue(result.highRiskHit());
        assertEquals(0.8, result.textNeg());
    }
}
