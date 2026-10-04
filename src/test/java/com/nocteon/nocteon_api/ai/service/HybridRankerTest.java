package com.nocteon.nocteon_api.ai.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure arithmetic on {@link HybridRanker}. No Spring context and no mocks: the ranker has
 * no collaborators, and giving it one would be the bug this suite exists to prevent.
 */
@ExtendWith(MockitoExtension.class)
class HybridRankerTest {

    private final HybridRanker ranker = new HybridRanker();

    @Test
    void vectorHeavySignalDominatesWhenBothPresent() {
        Map<Long, Double> scores = ranker.blend(List.of(1L), Map.of(1L, 0.9, 2L, 0.95));

        assertThat(scores.get(2L)).isGreaterThan(scores.get(1L));
    }

    @Test
    void keywordOnlyHitScoresAboveZero() {
        Map<Long, Double> scores = ranker.blend(List.of(5L, 6L), Map.of());

        assertThat(scores.get(5L)).isPositive();
        assertThat(scores.get(6L)).isPositive();
        assertThat(scores.get(5L)).isGreaterThan(scores.get(6L));
    }

    @Test
    void keywordOnlyOutranksWeakVectorHit() {
        // rank 0 keyword = 1/60 ≈ 0.0167 → 0.4*0.0167 ≈ 0.0067
        // vector 0.01 → 0.6*0.01 = 0.006  ⇒ keyword wins, narrowly.
        Map<Long, Double> scores = ranker.blend(List.of(7L), Map.of(8L, 0.01));

        assertThat(scores.get(7L)).isGreaterThan(scores.get(8L));
    }

    @Test
    void idAbsentFromBothMapsGetsZeroNotNegative() {
        // product 9 appears in neither list/map
        Map<Long, Double> scores = ranker.blend(List.of(1L), Map.of(2L, 0.5));

        assertThat(scores).doesNotContainKey(9L);
    }

    @Test
    void everyInputIdAppearsInOutput() {
        Map<Long, Double> scores = ranker.blend(List.of(1L, 2L, 3L), Map.of(2L, 0.4, 4L, 0.6));

        assertThat(scores.keySet()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void emptyInputsYieldEmptyResult() {
        assertThat(ranker.blend(List.of(), Map.of())).isEmpty();
    }

    // --- exact-arithmetic pins -------------------------------------------------------
    // The ordering tests above would still pass under plausible-but-wrong constants, so the
    // weights and the rank constant are pinned to exact values here.

    @Test
    void keywordScoreUsesReciprocalRankConstantSixty() {
        Map<Long, Double> scores = ranker.blend(List.of(1L, 2L), Map.of());

        assertThat(scores.get(1L)).isEqualTo(0.4 * (1.0 / 60.0));
        assertThat(scores.get(2L)).isEqualTo(0.4 * (1.0 / 61.0));
    }

    @Test
    void weightsAreSixTenthsVectorAndFourTenthsKeyword() {
        // Id 1 is in both inputs, so its score must be exactly the weighted sum.
        Map<Long, Double> scores = ranker.blend(List.of(1L), Map.of(1L, 0.5));

        assertThat(scores.get(1L)).isEqualTo(0.6 * 0.5 + 0.4 * (1.0 / 60.0));
    }

    @Test
    void vectorOnlyIdScoresPureVectorSignal() {
        Map<Long, Double> scores = ranker.blend(List.of(), Map.of(3L, 0.8));

        assertThat(scores.get(3L)).isEqualTo(0.6 * 0.8);
    }

    @Test
    void keywordOnlyIdScoresPureKeywordSignal() {
        Map<Long, Double> scores = ranker.blend(List.of(3L), Map.of());

        assertThat(scores.get(3L)).isEqualTo(0.4 * (1.0 / 60.0));
    }

    @Test
    void keywordRankIsPositionalSoReorderingChangesScores() {
        Map<Long, Double> scores = ranker.blend(List.of(10L, 20L), Map.of());

        assertThat(scores.get(10L)).isGreaterThan(scores.get(20L));
    }

    @Test
    void blendDoesNotMutateItsInputs() {
        List<Long> keywordIds = List.of(1L, 2L);
        Map<Long, Double> vectorScores = Map.of(1L, 0.5);

        ranker.blend(keywordIds, vectorScores);

        assertThat(keywordIds).containsExactly(1L, 2L);
        assertThat(vectorScores).containsExactly(Map.entry(1L, 0.5));
    }
}
