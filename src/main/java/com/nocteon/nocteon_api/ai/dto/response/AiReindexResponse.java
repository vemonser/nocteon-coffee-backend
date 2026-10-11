package com.nocteon.nocteon_api.ai.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Outcome of one full reindex run.
 *
 * <p>{@code indexed} and {@code skipped} partition the active catalog: a product is
 * {@code skipped} when the vector store already holds a document for it whose
 * {@code contentHash} equals the hash of its current projection, and {@code indexed}
 * otherwise. They therefore sum to the number of active products seen by the run.
 *
 * <p>A call rejected by the reindex guard (another run already in flight) reports zeros,
 * which {@code durationMs} alone cannot distinguish from a genuine no-op run that finished in
 * under a millisecond — the {@code "AI reindex already in progress"} warning is the reliable
 * signal, so callers must not branch on these numbers to detect rejection.
 */
@Getter
@Builder
public class AiReindexResponse {

    private int indexed;
    private int skipped;
    private long durationMs;
}
