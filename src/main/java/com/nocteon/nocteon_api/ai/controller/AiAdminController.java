package com.nocteon.nocteon_api.ai.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.ai.dto.response.AiIndexStatsResponse;
import com.nocteon.nocteon_api.ai.dto.response.AiReindexResponse;
import com.nocteon.nocteon_api.ai.exception.AiUnavailableException;
import com.nocteon.nocteon_api.ai.service.AiSearchService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Admin surface for the AI product index: rebuild it, and see how far it has drifted.
 *
 * <p>{@code ai:manage} on both methods, which only {@code ADMIN} holds. It is deliberately not
 * {@code product:update}: a reindex re-embeds the entire catalog and therefore costs a full sweep
 * of the embedding model, so "can edit a product" is not the same authority as "can spend that".
 *
 * <h2>Why /stats is cached</h2>
 * {@link AiSearchService#indexStats()} costs one embedding call per active product, because
 * staleness is detected by searching the store with each product's own text. That is fine for an
 * admin who opens the page once and ruinous for a dashboard that polls it every few seconds — which
 * is exactly what a stats widget does. So a snapshot is held for {@link #STATS_TTL} and served from
 * there in the meantime, turning N embedding calls per poll into N per minute. A minute is short
 * enough that the numbers are still an honest picture of the index, and a successful reindex drops
 * the snapshot outright so the admin who just triggered a run sees its effect immediately rather
 * than after the TTL.
 *
 * <h2>Why both endpoints degrade instead of throwing</h2>
 * Neither the reindex nor the stats call degrades at the service level: both propagate whatever the
 * vector store and the embedding model throw. This class is the last line of defence, and the
 * project-wide constraint is that AI unavailability must never surface as a 500. So a failure is
 * logged at {@code warn} — message only, since a stack trace per poll would flood the log exactly
 * when the outage is hardest to read — and re-raised as {@link AiUnavailableException}, which the
 * global handler turns into a 503 with the standard localized "AI features are temporarily
 * unavailable". That status is the honest one: the cause is an unreachable dependency, so clients
 * and monitoring should treat it as retryable. Failures are never cached, so the moment the
 * embedding model comes back the next poll succeeds.
 */
@RestController
@RequestMapping("/api/dashboard/ai")
@RequiredArgsConstructor
@Slf4j
public class AiAdminController {

    /**
     * How long a drift snapshot is served before it is recomputed. Bounded below by the cost of one
     * snapshot and above by how stale an admin is willing to be told their index is.
     */
    private static final Duration STATS_TTL = Duration.ofSeconds(60);

    private final AiSearchService aiSearchService;

    /**
     * The last computed snapshot. A plain {@link AtomicReference} rather than a cache: one entry,
     * no eviction, no invalidation map — and a concurrent recompute is harmless because the work is
     * idempotent, so racing requests cost one extra snapshot between them rather than locking a
     * request thread.
     */
    private final AtomicReference<CachedStats> cachedStats = new AtomicReference<>();

    /**
     * Rebuilds the whole index from the active catalog.
     *
     * <p>Not flag-guarded: an admin must be able to repair a store that the search flag disabled in
     * the first place. The in-flight guard lives in {@code ProductIndexingService}, so a second
     * concurrent run is turned away there rather than being hidden here.
     *
     * @return the run's counts and duration; zeros when another run already holds the guard
     * @throws AiUnavailableException when the embedding model cannot be reached — a 503, not a 500
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasAuthority('ai:manage')")
    public ResponseEntity<ApiResponse<AiReindexResponse>> reindex() {
        try {
            AiReindexResponse result = aiSearchService.reindexAll();
            // The snapshot this run supersedes would otherwise be served for up to a whole TTL.
            cachedStats.set(null);
            return ResponseEntity.ok(ApiResponse.success(result, "Reindex completed"));
        } catch (Exception e) {
            log.warn("AI reindex unavailable: {}", e.getMessage());
            throw new AiUnavailableException();
        }
    }

    /**
     * How far the index has drifted from the active catalog.
     *
     * @return the drift snapshot, recomputed at most once per {@link #STATS_TTL}
     * @throws AiUnavailableException when the embedding model cannot be reached — a 503, not a 500
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('ai:manage')")
    public ResponseEntity<ApiResponse<AiIndexStatsResponse>> stats() {
        CachedStats cached = cachedStats.get();

        if (cached != null && isFresh(cached.computedAt(), Instant.now(), STATS_TTL)) {
            return ResponseEntity.ok(ApiResponse.success(cached.snapshot(), "Index stats retrieved"));
        }

        try {
            AiIndexStatsResponse snapshot = aiSearchService.indexStats();
            cachedStats.set(new CachedStats(snapshot, Instant.now()));
            return ResponseEntity.ok(ApiResponse.success(snapshot, "Index stats retrieved"));
        } catch (Exception e) {
            log.warn("AI index stats unavailable: {}", e.getMessage());
            throw new AiUnavailableException();
        }
    }

    /**
     * Whether a snapshot taken at {@code computedAt} may still be served at {@code now}.
     *
     * <p>Half-open on purpose: at exactly the TTL the snapshot is already too old, so the boundary
     * belongs to the expiry side and a snapshot can never be served for longer than the TTL, not
     * even by one instant.
     */
    static boolean isFresh(Instant computedAt, Instant now, Duration ttl) {
        return computedAt.plus(ttl).isAfter(now);
    }

    /** A measured snapshot and when it was measured. Immutable, so it can be published as one. */
    private record CachedStats(AiIndexStatsResponse snapshot, Instant computedAt) {
    }
}