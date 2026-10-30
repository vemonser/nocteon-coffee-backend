package com.nocteon.nocteon_api.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the audit-log retention job.
 *
 * <p>Bound from {@code app.audit.retention.*}, which defaults to
 * {@code ${AUDIT_RETENTION_ENABLED:false}} / {@code ${AUDIT_RETENTION_DAYS:180}} /
 * {@code ${AUDIT_RETENTION_BATCH_SIZE:5000}} in {@code application.yaml}, and is
 * registered by {@code SchedulingConfig}.
 *
 * <h2>Why the gate exists, and why it defaults to off</h2>
 * This is the only job in the application that <em>destroys</em> rows other
 * people wrote. {@code audit_logs} is the record of who did what — an
 * investigation that needs a row two years old cannot be undone by re-enabling
 * a flag. Deleting is therefore a deliberate, per-environment decision, in the
 * same spirit as {@code nocteon.mail.scheduling-enabled}: a deploy that happens
 * to turn on scheduling must not quietly start truncating history. Until
 * {@code AUDIT_RETENTION_ENABLED=true} is set, the scheduled method returns
 * before it touches {@code audit_logs}.
 *
 * <p>The getter is Lombok-generated for a {@code boolean} field, so it reads
 * {@code isEnabled()}, not {@code getEnabled()}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.audit.retention")
public class AuditRetentionProperties {

    /**
     * Whether the retention job may run. {@code false} leaves the bean in place
     * but inert: the scheduled method returns before running a single query.
     */
    private boolean enabled = false;

    /**
     * Rows whose {@code created_at} is older than this many days are eligible
     * for deletion. Rows younger than the horizon are never touched. Default
     * 180 days — six months is enough to answer "who changed this, and when"
     * for a support or audit question, and short enough that the table stays a
     * few hundred thousand rows instead of tens of millions.
     */
    private int days = 180;

    /**
     * How many rows one delete statement removes. Each batch is its own
     * transaction, so the job commits and releases its locks every
     * {@code batch-size} rows instead of holding one transaction for the whole
     * backlog. Default 5000.
     */
    private int batchSize = 5000;
}
