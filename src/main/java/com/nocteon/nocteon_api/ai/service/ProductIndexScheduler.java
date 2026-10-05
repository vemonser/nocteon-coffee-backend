package com.nocteon.nocteon_api.ai.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.ai.dto.response.AiReindexResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The nightly self-heal for the vector index.
 *
 * <p>The post-commit hook in the product service keeps single products current as they are edited,
 * but nothing keeps the <em>store</em> honest: a run that failed while Ollama was down leaves a gap
 * that no later edit repairs, because a product nobody touches again is never re-embedded. This job
 * is the backstop — once a night it asks {@link ProductIndexingService#indexAll()} to re-embed
 * anything whose {@code contentHash} no longer matches the stored document, which is a no-op write
 * for the products that are already current.
 *
 * <h2>Why it stays quiet</h2>
 * <ul>
 * <li>The flag is read <em>first</em>. With AI switched off there is nothing to repair, and the run
 * must cost a property read rather than a catalog scan.</li>
 * <li>{@code reindexAll()} is not flag-guarded (see {@link AiSearchService}) precisely so an admin
 * can repair a store the flag disabled; that is exactly what makes an unhandled exception here
 * possible, so the catch is broad and the message-only {@code warn} is deliberate — a stack trace
 * per failed night would say nothing the message does not.</li>
 * <li>The catch spans the flag read too, and nothing is thrown out of this method. Spring logs an
 * exception escaping a {@code @Scheduled} task and then quietly stops rescheduling it, so an
 * unhandled failure here would turn a one-night outage into a permanently dead job.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductIndexScheduler {

    private final AiSearchService aiSearchService;

    @Scheduled(cron = "0 0 3 * * *")
    public void nightlyReindex() {
        try {
            if (!aiSearchService.isSearchEnabled()) {
                return;
            }

            AiReindexResponse result = aiSearchService.reindexAll();
            log.info("Nightly AI reindex: indexed={}, skipped={}, took={}ms",
                    result.getIndexed(), result.getSkipped(), result.getDurationMs());
        } catch (Exception e) {
            log.warn("Nightly AI reindex failed: {}", e.getMessage());
        }
    }
}