package com.nocteon.nocteon_api.ai.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Blends the keyword ranking with the vector ranking into one score per product.
 *
 * <p>Pure by construction: no collaborators, no I/O, no clock. Everything it needs arrives as
 * arguments, which is what makes the arithmetic directly unit-testable and lets callers use it
 * without a Spring context.
 *
 * <h2>The arithmetic</h2>
 * A keyword hit at 0-based rank {@code r} scores {@code 1 / (60 + r)} — reciprocal-rank, so
 * being first matters far more than being tenth but the tail never collapses to nothing. The
 * two signals are then combined as {@code 0.4 * keyword + 0.6 * vector}.
 *
 * <p>The vector signal is weighted higher because it carries the information keyword search
 * structurally cannot: {@code "fruity"} matching a product described as "berry notes" is the
 * entire point of the feature, and a weight below 0.5 would let exact-name matches bury it.
 *
 * <p>{@link Map#merge} with {@link Double#sum} gives exactly the semantics wanted without a
 * separate presence set: an id present in only one input gets that signal's weighted score,
 * an id in both gets the sum, and an id in neither never enters the map at all — rather than
 * entering it as a {@code 0.0} that would tie against a genuine weak match.
 *
 * <p>No clamping is applied. The vector scores reaching this method are cosine similarities
 * from {@link org.springframework.ai.vectorstore.VectorStore#similaritySearch}, which are
 * already in {@code [0,1]} for a cosine distance store, and a negative keyword score is
 * impossible since {@code 1/(60+r)} is positive for every rank.
 */
@Component
public class HybridRanker {

    /** Weight of the reciprocal-rank keyword signal. */
    private static final double KEYWORD_WEIGHT = 0.4;

    /** Weight of the vector similarity signal. */
    private static final double VECTOR_WEIGHT = 0.6;

    /**
     * Offset in the reciprocal-rank formula. Larger values flatten the curve, so the gap
     * between rank 0 and rank 1 is small relative to the tail; 60 is the conventional
     * constant from the original reciprocal-rank fusion work.
     */
    private static final double RANK_CONSTANT = 60.0;

    /**
     * Blends the two rankings into a single score per product id.
     *
     * <p>Neither argument is mutated, and the result is insertion-ordered: keyword hits in
     * rank order first, then vector-only hits in their incoming order.
     *
     * @param keywordRankedIds product ids in descending keyword-relevance order, best first
     * @param vectorScores     product id to similarity in {@code [0,1]}, higher is more similar
     * @return product id to blended score; every id from either input is present, and no
     *         other id is
     */
    public Map<Long, Double> blend(List<Long> keywordRankedIds, Map<Long, Double> vectorScores) {
        Map<Long, Double> blended = new LinkedHashMap<>();

        for (int i = 0; i < keywordRankedIds.size(); i++) {
            double keywordScore = 1.0 / (RANK_CONSTANT + i);
            blended.merge(keywordRankedIds.get(i), KEYWORD_WEIGHT * keywordScore, Double::sum);
        }

        vectorScores.forEach((id, score) -> blended.merge(id, VECTOR_WEIGHT * score, Double::sum));

        return blended;
    }
}
