package com.nocteon.nocteon_api.audit.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.audit.entity.AuditLog;
import com.nocteon.nocteon_api.audit.enums.AuditAction;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:userId IS NULL OR a.userId = :userId)
            AND (:action IS NULL OR a.action = :action)
            AND (:entityType IS NULL OR a.entityType = :entityType)
            AND (:entityId IS NULL OR a.entityId = :entityId)
            AND (:since IS NULL OR a.createdAt >= :since)
            AND (:until IS NULL OR a.createdAt <= :until)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> findAllFiltered(
            @Param("userId") Long userId,
            @Param("action") AuditAction action,
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
}