package com.nocteon.nocteon_api.audit.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.audit.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * The audit page's query.
     *
     * <p>Every filter is optional: a null argument means "no filter on this
     * column", so the dashboard can send only the ones the reader filled in.
     *
     * <p>{@code actionPattern} is a LIKE pattern over the action NAME (the
     * column stores the enum's name), so "product" finds every PRODUCT_* row.
     * {@code actorId} is compared exactly; {@code actorEmailPattern} is a LIKE
     * pattern over the stored email and is only supplied for a text actor.
     *
     * <p>The ORDER BY is applied from the pageable (see
     * {@code AuditLogService#stablePageable}), which appends the id so paging
     * stays stable when two rows share a microsecond timestamp.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:actionPattern IS NULL OR LOWER(CAST(a.action AS string)) LIKE :actionPattern)
            AND (:actorId IS NULL OR a.userId = :actorId)
            AND (:actorEmailPattern IS NULL OR LOWER(COALESCE(a.userEmail, '')) LIKE :actorEmailPattern)
            AND (:entityType IS NULL OR a.entityType = :entityType)
            AND (:entityId IS NULL OR a.entityId = :entityId)
            AND a.createdAt >= :since
            AND a.createdAt <= :until
            """)
    Page<AuditLog> search(
            @Param("actionPattern") String actionPattern,
            @Param("actorId") Long actorId,
            @Param("actorEmailPattern") String actorEmailPattern,
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId,
            @Param("since") Instant since,
            @Param("until") Instant until,
            Pageable pageable);

    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.entityType = :entityType
            AND a.entityId = :entityId
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> findByEntity(@Param("entityType") String entityType, @Param("entityId") Long entityId);

    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.userId = :userId
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    /**
     * Deletes at most {@code batchSize} rows whose {@code created_at} is
     * strictly before {@code horizon} and returns how many rows went.
     *
     * <p>Three deliberate details:
     * <ul>
     * <li>{@code created_at < :horizon} — a row at the horizon itself stays.
     * This is the whole safety property of the job: it can never delete
     * something younger than the configured window.</li>
     * <li>{@code LIMIT :batchSize} — the statement is bounded. The caller runs
     * it in a loop, one transaction per batch, so no single statement holds a
     * long lock on {@code audit_logs}.</li>
     * <li>{@code ORDER BY created_at, id} — lets {@code idx_audit_log_created}
     * serve both the filter and the order, so Postgres stops after
     * {@code batchSize} index entries instead of sorting the backlog. The id
     * breaks ties so a batch boundary is deterministic.</li>
     * </ul>
     *
     * <p>Native SQL rather than JPQL: the delete must be a bounded
     * {@code LIMIT} in the database, and the {@code @Modifying @Query} JPQL form
     * has no portable way to say "at most N rows".
     */
    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM audit_logs
            WHERE id IN (
                SELECT id FROM audit_logs
                WHERE created_at < :horizon
                ORDER BY created_at, id
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteBatchOlderThan(@Param("horizon") Instant horizon, @Param("batchSize") int batchSize);
}
