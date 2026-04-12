package com.wuhao.aiemotion.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextNegScorerTest {

    private final TextNegScorer scorer = new TextNegScorer(10.0D, 0.8D);

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
        assertEquals(0.0D, result.diagnosticScore());
        assertFalse(result.highRiskHit());
    }

    @Test
    void shouldNotMatchEnglishSubstringInsideWord() {
        TextNegScorer.TextNegScoreResult result = scorer.score("this is hispanic music");

        assertEquals(0.0D, result.textNeg());
        assertEquals(0, result.hitCount());
    }

    @Test
    void shouldNotMatchEnglishInsideSnakeCaseToken() {
        TextNegScorer.TextNegScoreResult result = scorer.score("signal panic_attack captured");

        assertEquals(0.0D, result.textNeg());
        assertEquals(0, result.hitCount());
    }

    @Test
    void shouldNotLeakDegreeAcrossClauses() {
        TextNegScorer.TextNegScoreResult crossClause = scorer.score("我昨天非常累。今天有点焦虑");
        TextNegScorer.TextNegScoreResult sameClauseStrong = scorer.score("我今天非常焦虑");

        assertTrue(crossClause.textNeg() < sameClauseStrong.textNeg());
    }

    @Test
    void shouldExposePositiveDiagnosticHitsEvenWhenNegativeScoreIsZero() {
        TextNegScorer.TextNegScoreResult result = scorer.score("我今天很开心，心情很好");

        assertEquals(0.0D, result.textNeg());
        assertEquals(0, result.hitCount());
        assertTrue(result.diagnosticScore() > 0.0D);
        assertEquals("HAP", result.dominantEmotion());
        assertTrue(result.hits().stream().anyMatch(hit -> hit.startsWith("HAP:")));
    }

    @Test
    void shouldExposeGroupedDiagnosticEmotionScores() {
        TextNegScorer.TextNegScoreResult result = scorer.score("我现在真的很生气，而且特别难过");

        assertTrue(result.textNeg() > 0.0D);
        assertTrue(result.diagnosticHitCount() > 0);
        assertNotEquals(0.0D, result.emotion4Scores().getOrDefault("ANG", 0.0D));
        assertNotEquals(0.0D, result.emotion4Scores().getOrDefault("SAD", 0.0D));
    }
}
