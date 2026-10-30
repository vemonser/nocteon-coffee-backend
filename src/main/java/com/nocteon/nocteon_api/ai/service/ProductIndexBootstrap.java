package com.nocteon.nocteon_api.ai.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.ai.dto.response.AiIndexStatsResponse;
import com.nocteon.nocteon_api.ai.dto.response.AiReindexResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds the vector index once, on a fresh database, so semantic search works before anyone has
 * thought to press reindex — and states, once, which embedding mode the application is in.
 *
 * <h2>The gap this closes</h2>
 * A brand-new database reaches this point with the catalog fully seeded but {@code vector_store}
 * empty. Nothing fills it:
 * <ul>
 * <li>{@link ProductIndexScheduler} runs at 03:00 and only when {@code AI_ENABLED} and
 * {@code AI_SEARCH_ENABLED} are both set — both default to false;</li>
 * <li>the admin reindex endpoint needs the {@code ai:manage} permission, so it is not something a
 * deploy script or a first-time contributor will reach for.</li>
 * </ul>
 * The result is the worst kind of silent failure: the app starts, health checks pass, {@code
 * /api/products?semantic=true} returns 200 with results — and those results come only from the
 * keyword half of the hybrid ranker, because the semantic half has nothing to contribute. Measured
 * on a fresh database: the assistant answered "I could not find a chocolatey coffee" with zero
 * product cards while the catalog plainly held four.
 *
 * <h2>The startup line</h2>
 * With no embedding provider configured this logs exactly one {@code INFO} — "no provider, search
 * is keyword-only" — and returns. That is the honest statement of the mode the application is
 * running in, it is the line to grep after a deploy, and it replaces what used to be a silent
 * fallback to a local Ollama that the deployment host does not run. It is logged before the
 * feature flags are read on purpose: the mode is a property of the configuration, not of whether
 * search is currently switched on, and an operator looking for it should not have to enable search
 * to find out it cannot work.
 *
 * <h2>Why it is deliberately narrow</h2>
 * <ul>
 * <li>It only runs when the index is <em>empty or stale</em>. A restart of a healthy instance does
 * a stats read and stops — it does not re-embed the catalog on every deploy.</li>
 * <li>Nothing is thrown. A missing embedding provider must degrade to "no semantic search", not
 * to a failed startup, so the failure is logged and startup continues.</li>
 * <li>{@link ApplicationReadyEvent} rather than {@code @PostConstruct}, because the vector store and
 * the datasource are both fully initialised by then.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductIndexBootstrap {

    private final AiSearchService aiSearchService;
    private final ProductIndexingService indexingService;

    @EventListener(ApplicationReadyEvent.class)
    public void buildIndexIfEmpty() {
        if (!aiSearchService.isSemanticIndexAvailable()) {
            // Keyword-only by configuration. One line, and then nothing: no vector store, no
            // embedding call, no local model, no retry.
            log.info("No embedding provider is configured, so semantic search is keyword-only and no "
                    + "vector index is built. Set GEMINI_API_KEY, or AI_EMBEDDING_PROVIDER="
                    + "openai-compatible with EMBEDDING_BASE_URL/EMBEDDING_API_KEY/EMBEDDING_MODEL, or "
                    + "AI_EMBEDDING_PROVIDER=ollama for a local model.");
            return;
        }

        try {
            if (!aiSearchService.isSearchEnabled()) {
                return;
            }

            AiIndexStatsResponse stats = indexingService.stats();
            if (stats.getIndexed() > 0 && stats.getStale() == 0) {
                return;
            }

            AiReindexResponse result = indexingService.indexAll();
            log.info("Built the AI vector index on startup: indexed={}, skipped={}, took={}ms",
                    result.getIndexed(), result.getSkipped(), result.getDurationMs());
        } catch (Exception e) {
            // Message-only, same reasoning as the nightly job: with an unreachable provider this
            // runs on every deploy and a stack trace each time would say nothing the message does
            // not. Search degrades to keyword-only, which is the documented no-key behaviour.
            log.warn("Could not build the AI vector index on startup; semantic search will stay "
                    + "keyword-only until the next reindex: {}", e.getMessage());
        }
    }
}
