package com.nocteon.nocteon_api.ai.dto.response;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;

/**
 * Snapshot of how far the vector store has drifted from the catalog.
 *
 * <p>{@code total} counts the active products that are candidates for indexing and
 * {@code stale} counts those whose current {@code contentHash} is not the one stored in
 * the vector store — either because the stored hash differs or because no document for
 * them exists yet. {@code indexed} is the remainder, i.e. the products the store already
 * reflects, and is derived as {@code total - stale} because Spring AI 2.0.1 exposes no way
 * to count documents in a store.
 *
 * <p>{@code lastRunAt} is the instant the last reindex attempt finished — including a
 * failed one, which is why it is stamped in a {@code finally} block — and stays
 * {@code null} until the first attempt.
 */
@Getter
@Builder
public class AiIndexStatsResponse {

    private int indexed;
    private int total;
    private int stale;
    private Instant lastRunAt;
}
