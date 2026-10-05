package com.nocteon.nocteon_api.ai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.ai.dto.response.AiReindexResponse;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the nightly reindex.
 *
 * <p>Two things are pinned here, and they are the reason this class exists rather than being a
 * bare {@code @Scheduled} call somewhere else. The feature flag is consulted <em>first</em>, so a
 * deployment with AI switched off costs one property read and no database work; and every failure
 * is swallowed, because a scheduler task that lets an exception out stops being rescheduled.
 */
@ExtendWith(MockitoExtension.class)
class ProductIndexSchedulerTest {

    @Mock
    private AiSearchService aiSearchService;

    @InjectMocks
    private ProductIndexScheduler scheduler;

    @Test
    void aNightlyRunWithAiOffNeverTouchesTheIndex() {
        when(aiSearchService.isSearchEnabled()).thenReturn(false);

        scheduler.nightlyReindex();

        verify(aiSearchService, never()).reindexAll();
    }

    /**
     * The flag is read <em>before</em> the reindex is attempted, not merely read at some point: an
     * implementation that reindexed first and checked afterwards would still satisfy every other
     * test here while reindexing a subsystem that is switched off.
     */
    @Test
    void theFlagIsCheckedBeforeAnyReindexWorkStarts() {
        when(aiSearchService.isSearchEnabled()).thenReturn(false);

        scheduler.nightlyReindex();

        InOrder order = inOrder(aiSearchService);
        order.verify(aiSearchService).isSearchEnabled();
        order.verify(aiSearchService, never()).reindexAll();
    }

    @Test
    void aNightlyRunWithAiOnReindexesTheCatalog() {
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.reindexAll())
                .thenReturn(AiReindexResponse.builder().indexed(4).skipped(6).durationMs(12L).build());

        scheduler.nightlyReindex();

        verify(aiSearchService).reindexAll();
    }

    /**
     * Ollama down at 3am must not become a stack trace, and above all must not propagate: an
     * exception escaping a {@code @Scheduled} method is swallowed by the task scheduler's error
     * handler and the task then silently stops being retried, which is precisely the failure this
     * run exists to repair.
     */
    @Test
    void aFailedNightlyRunDoesNotEscape() {
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.reindexAll()).thenThrow(new IllegalStateException("ollama down"));

        assertThatCode(() -> scheduler.nightlyReindex()).doesNotThrowAnyException();
    }

    /**
     * The flag read is not exempt from the catch either: an unreadable properties binding would
     * otherwise be the one failure that does take the run down.
     */
    @Test
    void aFailureReadingTheFlagDoesNotEscapeEither() {
        AiSearchService exploding = mock(AiSearchService.class);
        when(exploding.isSearchEnabled()).thenThrow(new IllegalStateException("properties unreadable"));

        assertThatCode(() -> new ProductIndexScheduler(exploding).nightlyReindex())
                .doesNotThrowAnyException();
    }

    /** A run turned away by the in-flight guard reports zeros; that is an outcome, not a fault. */
    @Test
    void aRunRejectedByTheInFlightGuardIsNotAFailure() {
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.reindexAll())
                .thenReturn(AiReindexResponse.builder().indexed(0).skipped(0).durationMs(0).build());

        assertThatCode(() -> scheduler.nightlyReindex()).doesNotThrowAnyException();
    }

    /** One bad night must not wedge the scheduler: the next run happens as if nothing happened. */
    @Test
    void aFailedRunDoesNotStopTheNextOne() {
        when(aiSearchService.isSearchEnabled()).thenReturn(true);
        when(aiSearchService.reindexAll())
                .thenThrow(new IllegalStateException("ollama down"))
                .thenReturn(AiReindexResponse.builder().indexed(2).skipped(0).durationMs(3L).build());

        scheduler.nightlyReindex();
        scheduler.nightlyReindex();

        verify(aiSearchService, times(2)).reindexAll();
    }
}