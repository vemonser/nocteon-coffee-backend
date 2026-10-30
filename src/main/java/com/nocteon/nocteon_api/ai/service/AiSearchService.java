package com.nocteon.nocteon_api.ai.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.ai.config.AiProperties;
import com.nocteon.nocteon_api.ai.dto.response.AiIndexStatsResponse;
import com.nocteon.nocteon_api.ai.dto.response.AiReindexResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The single entry point to the AI capabilities for everything downstream of indexing.
 *
 * <p>Controllers and search implementations talk to this facade and never to
 * {@link VectorStore} directly, so the feature flags are enforced in exactly one place and no
 * caller can forget to check them.
 *
 * <h2>Degradation is a feature, not an error path</h2>
 * {@link #semanticCandidates(String, int)} returns an empty map — never throws — when the
 * feature is off, when the query is blank, when there is no vector index at all, or when the
 * embedding model is unreachable. Whether that model is Gemini, a hosted OpenAI-compatible vendor
 * or a local Ollama, the answer must be the same: the site degrades to plain keyword search rather
 * than turning every catalog page into a 500. So the catch is deliberately broad — a downed
 * provider surfaces as a connection error, an SDK exception or a rate limit, and all of them must
 * degrade identically — and the log line is a {@code warn} carrying only the message: a stack
 * trace per request would flood the log precisely when the failure is hardest to diagnose.
 *
 * <h2>Why the vector store is optional</h2>
 * With no embedding provider configured the application runs keyword-only and no
 * {@code VectorStore} bean exists at all, because {@code PgVectorStore} cannot be constructed
 * without an {@code EmbeddingModel} (see
 * {@link com.nocteon.nocteon_api.ai.config.EmbeddingProviderEnvironmentPostProcessor}). An
 * {@link ObjectProvider} is how that is expressed: {@link #isSemanticIndexAvailable()} answers
 * "does an index exist", and every path here treats "no" as an ordinary outcome rather than a
 * failure. A distinct bean type was rejected — it would have introduced a second source of truth
 * for the same fact, and could disagree with the wiring.
 *
 * <h2>Why the query is prefixed here</h2>
 * This is the query half of the asymmetric-retrieval pair; {@link EmbeddingTaskPrefix} holds both
 * halves and explains why the intent is in the text at all. The important consequence is that this
 * remains the only path to the vector store for user input, so a hosted provider's failure is
 * caught here no matter which provider is wired.
 *
 * <p>{@link #reindexAll()} and {@link #indexStats()} are thin delegates to
 * {@link ProductIndexingService} and are not flag-guarded. They are admin operations whose
 * authorization is enforced by the calling controller's {@code @PreAuthorize}; gating them
 * here would make an admin unable to inspect, or repair, a store that the search flag
 * disabled in the first place.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiSearchService {

    /** Metadata key holding the product id on an indexed document. */
    private static final String PRODUCT_ID_KEY = "productId";

    /**
     * Accept-everything similarity threshold.
     *
     * <p>Deliberately zero, and deliberately not a tuned number: cosine similarity is not
     * calibrated across embedding models — the same query against the same product scores
     * differently under a different model or dimension — so any absolute cutoff would encode
     * a value that silently stops meaning anything after a model swap. Filtering is left to
     * {@link HybridRanker}, which compares scores only against each other.
     */
    private static final double ACCEPT_ALL_SIMILARITY = 0.0;

    private final ObjectProvider<VectorStore> vectorStore;
    private final ProductIndexingService indexingService;
    private final AiProperties properties;

    /**
     * Whether semantic search may run at all.
     *
     * <p>Requires both the master switch and the search switch: the master switch must be able
     * to turn the whole subsystem off in one place during an incident, which it could not do
     * if any feature flag were sufficient on its own. It says nothing about whether an index
     * exists — that is {@link #isSemanticIndexAvailable()}.
     *
     * @return true only when {@code enabled} and {@code searchEnabled} are both true
     */
    public boolean isSearchEnabled() {
        return properties.isEnabled() && properties.isSearchEnabled();
    }

    /**
     * Whether this application has a vector index to search.
     *
     * <p>False exactly when no embedding provider is configured: the store bean is absent, so
     * there is nothing indexed and nothing that could embed a query. Search is then keyword-only
     * by design, which {@code ProductIndexBootstrap} states once at startup and
     * {@link ProductIndexScheduler} respects instead of retrying a chain that cannot work.
     *
     * @return true when a {@code VectorStore} is wired
     */
    public boolean isSemanticIndexAvailable() {
        return vectorStore.getIfAvailable() != null;
    }

    /**
     * Fetches the vector-store candidates for a query, mapped to product ids.
     *
     * <p>Returns an empty map on every failure path — disabled, blank query, no index, or an
     * unreachable embedding model — so callers can treat "no semantic signal" as an ordinary
     * outcome and fall back to keyword ranking.
     *
     * @param query the user's search text; blank or null yields an empty map without ever
     *              calling the vector store
     * @param topK  how many candidates to request from the vector store
     * @return product id to cosine similarity, higher meaning more similar; empty when
     *         semantic search is unavailable
     */
    public Map<Long, Double> semanticCandidates(String query, int topK) {
        if (!isSearchEnabled()) {
            return Map.of();
        }

        // Checked before any vector-store call: a blank string still reaches the embedding model,
        // and an embedding model returns garbage or errors on empty input. Embedding nothing to
        // "find something" is both wasteful and actively harmful. Checked on the raw query rather
        // than the prefixed one so a prefix alone can never make a blank query look non-blank.
        if (!StringUtils.hasText(query)) {
            return Map.of();
        }

        VectorStore store = vectorStore.getIfAvailable();
        if (store == null) {
            // Keyword-only mode. Not a warning: this is a configured state, not a fault, and it
            // would otherwise repeat on every catalog request.
            return Map.of();
        }

        try {
            List<Document> hits = store.similaritySearch(SearchRequest.builder()
                    .query(EmbeddingTaskPrefix.forQuery(query))
                    .topK(topK)
                    .similarityThreshold(ACCEPT_ALL_SIMILARITY)
                    .build());

            Map<Long, Double> candidates = new LinkedHashMap<>();
            for (Document hit : hits) {
                toCandidate(hit).ifPresent(entry -> candidates.merge(
                        entry.getKey(), entry.getValue(), Math::max));
            }
            return candidates;
        } catch (Exception e) {
            // Broad on purpose: an unreachable provider surfaces as a connection error, a WebClient
            // exception, or a wrapped runtime exception depending on the transport; a hosted API
            // surfaces an SDK exception for a bad key, a rate limit, or an outage. All of them must
            // degrade the same way. Message only, no stack trace, so a persistent outage does not
            // flood the log once per request.
            log.warn("Semantic search unavailable, falling back to keyword only: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Turns one hit into a product id to similarity entry, or empty when the hit cannot be
     * scored or identified.
     *
     * <p>A hit with a missing {@code productId}, a non-numeric one, or a null score is
     * skipped rather than guessed at — the score is only meaningful alongside an id the
     * caller can resolve, and a null score means the store did not populate it.
     *
     * <p>The score is used as-is. Spring AI 2.0.1's
     * {@code PgVectorStore.DocumentRowMapper} already converts pgvector's distance column
     * into a similarity via {@code score = 1 - distance}, so {@link Document#getScore()} is a
     * similarity in {@code [0,1]} where higher is better. Converting again would invert the
     * ranking and promote the <em>least</em> similar product.
     */
    private Optional<Map.Entry<Long, Double>> toCandidate(Document hit) {
        Object productId = hit.getMetadata().get(PRODUCT_ID_KEY);
        Double score = hit.getScore();

        if (!(productId instanceof Number number) || score == null) {
            return Optional.empty();
        }
        return Optional.of(Map.entry(number.longValue(), score));
    }

    /**
     * Rebuilds the vector store from the active catalog.
     *
     * @return the run's counts and duration
     */
    public AiReindexResponse reindexAll() {
        return indexingService.indexAll();
    }

    /**
     * Reports how far the vector store has drifted from the active catalog.
     *
     * @return the drift snapshot
     */
    public AiIndexStatsResponse indexStats() {
        return indexingService.stats();
    }
}
