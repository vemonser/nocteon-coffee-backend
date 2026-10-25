package com.nocteon.nocteon_api.common.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a best-effort action only after the current transaction commits.
 *
 * <p>Cloudinary (and other external) side effects must never run inside the
 * transactional body: a failure there would roll back the database delete and
 * leave a restored row with a broken asset. Running after commit guarantees the
 * DB change is durable first; any failure in the action is logged, not thrown,
 * so it cannot undo the committed delete. Actions are best-effort; a failed
 * action is logged with enough context for manual retry/reconciliation.
 *
 * <p>If no transaction is active, the action runs inline (best effort) so that
 * non-transactional callers still get cleanup. This is a small, single-purpose
 * helper; it is not a scheduler, outbox, or general event bus.
 */
public final class AfterCommit {

    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        // Deferred (after-commit) execution requires an active Spring transaction.
        // If no transaction is active, the action runs inline as a best-effort
        // fallback (see below). All permanentDelete callers are @Transactional.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                action.run();
            } catch (Exception e) {
                log.warn("After-commit action failed (no active transaction): {}", e.getMessage(), e);
            }
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (Exception e) {
                    log.warn("After-commit action failed: {}", e.getMessage(), e);
                }
            }
        });
    }
}