package com.nocteon.nocteon_api.ai.controller;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import com.nocteon.nocteon_api.ai.dto.response.AiIndexStatsResponse;
import com.nocteon.nocteon_api.ai.dto.response.AiReindexResponse;
import com.nocteon.nocteon_api.ai.exception.AiUnavailableException;
import com.nocteon.nocteon_api.ai.service.AiSearchService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the admin reindex and stats endpoints.
 *
 * <p>Three contracts are pinned. First, authorization: {@code ai:manage} on both methods, asserted
 * reflectively because {@code @PreAuthorize} has no effect without a Spring context and a typo in
 * the authority string would otherwise be invisible to every test in this file. Second, the stats
 * snapshot is served from a short-lived cache, because building one costs an embedding call per
 * active product and an admin dashboard polling this every few seconds would otherwise hammer the
 * embedding model. Third, an unreachable embedding model produces a 503 rather than a 500: these
 * are the last endpoints standing between a downed Ollama and a stack trace.
 */
@ExtendWith(MockitoExtension.class)
class AiAdminControllerTest {

    @Mock
    private AiSearchService aiSearchService;

    @InjectMocks
    private AiAdminController controller;

    private static AiReindexResponse reindex(int indexed, int skipped, long durationMs) {
        return AiReindexResponse.builder()
                .indexed(indexed)
                .skipped(skipped)
                .durationMs(durationMs)
                .build();
    }

    private static AiIndexStatsResponse stats(int indexed, int total, int stale) {
        return AiIndexStatsResponse.builder()
                .indexed(indexed)
                .total(total)
                .stale(stale)
                .build();
    }

    // --- authorization -----------------------------------------------------------------------

    @Test
    void reindexIsGuardedByTheAiManageAuthority() throws Exception {
        Method reindex = AiAdminController.class.getDeclaredMethod("reindex");

        assertThat(reindex.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('ai:manage')");
    }

    @Test
    void statsIsGuardedByTheAiManageAuthority() throws Exception {
        Method stats = AiAdminController.class.getDeclaredMethod("stats");

        assertThat(stats.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('ai:manage')");
    }

    // --- reindex ------------------------------------------------------------------------------

    @Test
    void reindexHandsBackTheRunOutcome() {
        AiReindexResponse outcome = reindex(3, 7, 42L);
        when(aiSearchService.reindexAll()).thenReturn(outcome);

        ResponseEntity<ApiResponse<AiReindexResponse>> response = controller.reindex();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isSameAs(outcome);
    }

    /**
     * The embedding model being down is a retryable dependency failure, so it is reported as 503
     * through the shared handler rather than as a 500 from the catch-all.
     */
    @Test
    void aReindexAgainstADeadEmbeddingModelBecomes503Not500() {
        when(aiSearchService.reindexAll()).thenThrow(new IllegalStateException("ollama down"));

        assertThatThrownBy(controller::reindex)
                .isInstanceOf(AiUnavailableException.class)
                .satisfies(thrown -> {
                    AiUnavailableException failure = (AiUnavailableException) thrown;
                    assertThat(failure.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(failure.getMessageKey()).isEqualTo("error.ai.unavailable");
                });
    }

    // --- stats -------------------------------------------------------------------------------

    @Test
    void statsHandsBackTheDriftSnapshot() {
        AiIndexStatsResponse snapshot = stats(9, 10, 1);
        when(aiSearchService.indexStats()).thenReturn(snapshot);

        ResponseEntity<ApiResponse<AiIndexStatsResponse>> response = controller.stats();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isSameAs(snapshot);
    }

    /**
     * The carried review finding: one {@code indexStats()} call embeds every active product once.
     * A dashboard polling this endpoint must therefore not reach the model again until the cached
     * snapshot has expired.
     */
    @Test
    void repeatedStatsCallsWithinTheTtlAreServedFromTheCache() {
        AiIndexStatsResponse snapshot = stats(9, 10, 1);
        when(aiSearchService.indexStats()).thenReturn(snapshot);

        controller.stats();
        ResponseEntity<ApiResponse<AiIndexStatsResponse>> second = controller.stats();
        ResponseEntity<ApiResponse<AiIndexStatsResponse>> third = controller.stats();

        verify(aiSearchService, times(1)).indexStats();
        assertThat(second.getBody().getData()).isSameAs(snapshot);
        assertThat(third.getBody().getData()).isSameAs(snapshot);
    }

    /**
     * Caching must not outlive the thing that made it obsolete: an admin who clicks reindex and
     * immediately looks at the numbers is answering an already-resolved question, so the run drops
     * the cached snapshot.
     */
    @Test
    void aReindexDropsTheCachedStatsSoTheNextReadIsFresh() {
        when(aiSearchService.indexStats()).thenReturn(stats(9, 10, 1));
        when(aiSearchService.reindexAll()).thenReturn(reindex(10, 0, 5L));

        controller.stats();
        controller.reindex();
        controller.stats();

        verify(aiSearchService, times(2)).indexStats();
    }

    /**
     * Failures are never cached. Caching one would show the dashboard a green number it did not
     * measure and hide the moment the embedding model came back.
     */
    @Test
    void aFailedStatsCallIsNotCached() {
        when(aiSearchService.indexStats())
                .thenThrow(new IllegalStateException("ollama down"))
                .thenReturn(stats(10, 10, 0));

        assertThatThrownBy(controller::stats).isInstanceOf(AiUnavailableException.class);
        assertThatCode(() -> controller.stats()).doesNotThrowAnyException();

        verify(aiSearchService, times(2)).indexStats();
    }

    @Test
    void statsAgainstADeadEmbeddingModelBecomes503Not500() {
        when(aiSearchService.indexStats()).thenThrow(new IllegalStateException("ollama down"));

        assertThatThrownBy(controller::stats)
                .isInstanceOf(AiUnavailableException.class)
                .satisfies(thrown -> assertThat(((AiUnavailableException) thrown).getStatus())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    /**
     * A reindex that never ran must not be able to serve a stale snapshot: the invalidation
     * happens on the success path only, so a failed run leaves the last measured numbers alone.
     */
    @Test
    void aFailedReindexLeavesTheCachedStatsAlone() {
        when(aiSearchService.indexStats()).thenReturn(stats(9, 10, 1));
        when(aiSearchService.reindexAll()).thenThrow(new IllegalStateException("ollama down"));

        controller.stats();
        assertThatThrownBy(controller::reindex).isInstanceOf(AiUnavailableException.class);
        controller.stats();

        verify(aiSearchService, times(1)).indexStats();
    }

    // --- the freshness rule itself -------------------------------------------------------------

    /**
     * A snapshot is served until its ttl has fully elapsed. The arguments are
     * {@code (computedAt, now, ttl)}, so the first sits in the past by the snapshot's age.
     */
    @Test
    void aSnapshotIsFreshUntilItsTtlHasElapsed() {
        Instant now = Instant.parse("2026-10-05T03:00:00Z");
        Duration ttl = Duration.ofSeconds(60);

        assertThat(AiAdminController.isFresh(now, now, ttl)).isTrue();
        assertThat(AiAdminController.isFresh(now.minusSeconds(1), now, ttl)).isTrue();
        assertThat(AiAdminController.isFresh(now.minusSeconds(59), now, ttl)).isTrue();
        assertThat(AiAdminController.isFresh(now.minusSeconds(60), now, ttl)).isFalse();
        assertThat(AiAdminController.isFresh(now.minusSeconds(600), now, ttl)).isFalse();
    }

    /**
     * The boundary belongs to the expiry side, so a snapshot can never be served for longer than
     * the ttl — not even by a millisecond. That is what keeps the cache's worst case honest.
     */
    @Test
    void theTtlBoundaryIsHalfOpen() {
        Instant now = Instant.parse("2026-10-05T03:01:00Z");
        Duration ttl = Duration.ofSeconds(60);

        assertThat(AiAdminController.isFresh(now.minusMillis(59_999), now, ttl)).isTrue();
        assertThat(AiAdminController.isFresh(now.minusMillis(60_000), now, ttl)).isFalse();
        assertThat(AiAdminController.isFresh(now.minusMillis(60_001), now, ttl)).isFalse();
    }

    /** Nothing is served from a cache that was never filled. */
    @Test
    void aMissConsultsTheIndexingService() {
        when(aiSearchService.indexStats()).thenReturn(stats(0, 0, 0));

        controller.stats();

        verify(aiSearchService).indexStats();
    }

    /**
     * The reindex endpoint has no cached state of its own and always asks for a fresh run; the
     * in-flight guard lives in the indexing service, where a concurrent run is turned away rather
     * than being papered over here.
     */
    @Test
    void everyReindexCallReachesTheIndexingService() {
        when(aiSearchService.reindexAll()).thenReturn(reindex(1, 0, 1L));

        controller.reindex();
        controller.reindex();

        verify(aiSearchService, times(2)).reindexAll();
    }
}