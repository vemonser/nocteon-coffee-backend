package com.nocteon.nocteon_api.audit.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.audit.config.AuditRetentionProperties;
import com.nocteon.nocteon_api.audit.service.AuditLogRetentionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin trigger for the audit-log retention sweep — all behaviour lives in
 * {@link AuditLogRetentionService}, which is tested directly.
 *
 * <p>Gated behind {@link AuditRetentionProperties#isEnabled()}, which defaults
 * to {@code false}, the same pattern as {@code EmailRetryScheduler} and
 * {@code AbandonedCartScheduler}: deleting audit rows is not something a deploy
 * should do on its own initiative, so the job stays dormant until somebody sets
 * {@code AUDIT_RETENTION_ENABLED=true} deliberately.
 *
 * <p>The gate is the first statement, before any query, so a disabled
 * deployment never reads or writes {@code audit_logs}.
 *
 * <p>03:30 rather than 03:00 on purpose: {@code NotificationRetentionScheduler}
 * and {@code ProductIndexScheduler} both fire at 03:00, and a delete sweep that
 * competes with the AI reindex for connections is a self-inflicted stall.
 *
 * <h2>Why this method carries no {@code @Transactional}</h2>
 * Same reason as the mail jobs: the proxy would open the transaction
 * <em>before</em> the gate ran, so a disabled deployment would take a pooled
 * connection and commit an empty transaction every night. The transaction
 * belongs on the per-batch boundary inside
 * {@link AuditLogRetentionService#runRetention()}, which is only reached once
 * the gate has passed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogRetentionScheduler {

    private final AuditRetentionProperties properties;
    private final AuditLogRetentionService retentionService;

    @Scheduled(cron = "0 30 3 * * *")
    public void run() {
        if (!properties.isEnabled()) {
            log.debug("Audit log retention is disabled, skipping the cleanup pass");
            return;
        }

        retentionService.runRetention();
    }
}
