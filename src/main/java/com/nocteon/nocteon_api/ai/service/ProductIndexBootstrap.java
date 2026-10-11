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
 * thought to press reindex.
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
 * <h2>Why it is deliberately narrow</h2>
 * <ul>
 * <li>The flag is read first, so a deployment with AI off costs one property read.</li>
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
            // Message-only, same reasoning as the nightly job: without an embedding provider this
            // runs on every deploy and a stack trace each time would say nothing the message does
            // not. Search degrades to keyword-only, which is the documented no-key behaviour.
            log.warn("Could not build the AI vector index on startup; semantic search will stay "
                    + "keyword-only until the next reindex: {}", e.getMessage());
        }
    }
}