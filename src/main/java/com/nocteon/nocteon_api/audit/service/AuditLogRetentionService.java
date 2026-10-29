package com.nocteon.nocteon_api.audit.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.nocteon.nocteon_api.audit.config.AuditRetentionProperties;
import com.nocteon.nocteon_api.audit.repository.AuditLogRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Deletes audit rows older than the configured horizon, in small batches, so
 * the job never holds a long lock on {@code audit_logs}.
 *
 * <p>The horizon is computed once per pass; rows at or younger than it are
 * never touched (the delete predicate is {@code created_at < horizon}).
 *
 * <h2>Why batches, and why each one is its own transaction</h2>
 * A single {@code DELETE ... WHERE created_at < horizon} on a table that has
 * grown for a year would scan and lock the whole backlog in one transaction:
 * writers block, the vacuum can't keep up, and a cancelled request rolls back
 * hours of work. Instead every pass removes at most {@code batch-size} rows,
 * commits, releases its locks, and pauses briefly before the next one. Lock
 * waiters see a series of sub-second statements instead of one long one.
 *
 * <p>The loop stops as soon as a batch comes back short: that means the backlog
 * is drained, so a routine pass costs exactly one statement when there is
 * nothing to delete.
 *
 * <h2>Why an explicit {@code TransactionTemplate} instead of {@code @Transactional}</h2>
 * Same reason as {@code BulkOrderStatusService}: the method that loops must not
 * be one transaction, and calling a {@code @Transactional} method on
 * {@code this} would bypass the Spring proxy in silence. The template makes the
 * per-batch boundary explicit and non-negotiable.
 */
@Service
@Slf4j
public class AuditLogRetentionService {

    /**
     * Pause between batches. Long enough to let a waiting writer in and to keep
     * the pass from pinning a connection at 100% usage, short enough that a
     * backlog of a few hundred thousand rows still drains in minutes.
     */
    private static final Duration BATCH_PAUSE = Duration.ofMillis(100);

    private final AuditLogRepository auditLogRepository;
    private final AuditRetentionProperties properties;

    /**
     * The per-batch boundary: {@code REQUIRES_NEW} so a batch commits on its
     * own even if the caller happens to be inside a transaction.
     */
    private final TransactionTemplate perBatch;

    public AuditLogRetentionService(
            AuditLogRepository auditLogRepository,
            AuditRetentionProperties properties,
            PlatformTransactionManager transactionManager) {

        this.auditLogRepository = auditLogRepository;
        this.properties = properties;
        this.perBatch = new TransactionTemplate(transactionManager);
        this.perBatch.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Removes at most {@code batchSize} rows older than {@code horizon}, in its
     * own transaction, and returns how many rows went.
     */
    public int deleteBatch(Instant horizon, int batchSize) {
        Integer deleted = perBatch.execute(
                status -> auditLogRepository.deleteBatchOlderThan(horizon, batchSize));
        return deleted == null ? 0 : deleted;
    }

    /**
     * Runs the full retention pass: the horizon from the properties, then
     * batches until the backlog is drained.
     *
     * <p>Logs exactly one INFO line for the whole pass — the number deleted, the
     * horizon and the number of statements it took — never one line per row.
     *
     * @return the total number of rows deleted by this pass
     */
    public int runRetention() {
        int days = properties.getDays();
        int batchSize = properties.getBatchSize();

        // A zero/negative value is a configuration mistake, not a request to
        // delete everything: fail loudly instead of interpreting it.
        if (days < 1) {
            throw new IllegalStateException(
                    "app.audit.retention.days must be at least 1, was " + days);
        }
        if (batchSize < 1) {
            throw new IllegalStateException(
                    "app.audit.retention.batch-size must be at least 1, was " + batchSize);
        }

        Instant horizon = Instant.now().minus(days, ChronoUnit.DAYS);
        int total = 0;
        int statements = 0;

        while (true) {
            int deleted = deleteBatch(horizon, batchSize);
            total += deleted;
            statements++;

            if (deleted < batchSize) {
                break;
            }
            if (!pauseBetweenBatches()) {
                log.warn("Audit log retention was interrupted after {} rows; "
                        + "the remaining backlog is left for the next pass", total);
                return total;
            }
        }

        log.info("Audit log retention: deleted={} rows older than {} days "
                + "(horizon={}, statements={})", total, days, horizon, statements);
        return total;
    }

    /**
     * @return {@code true} when the pause completed, {@code false} when the
     *         thread was interrupted (the interrupt flag is restored)
     */
    private boolean pauseBetweenBatches() {
        try {
            Thread.sleep(BATCH_PAUSE.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
