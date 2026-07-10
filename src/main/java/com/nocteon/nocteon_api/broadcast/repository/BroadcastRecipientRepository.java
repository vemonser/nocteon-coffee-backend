package com.nocteon.nocteon_api.broadcast.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.broadcast.entity.BroadcastRecipient;
import com.nocteon.nocteon_api.broadcast.enums.BroadcastRecipientStatus;

public interface BroadcastRecipientRepository extends JpaRepository<BroadcastRecipient, Long> {

    @Query("""
        SELECT r FROM BroadcastRecipient r
        WHERE r.broadcast.id = :broadcastId
        AND r.status IN ('PENDING', 'FAILED')
        ORDER BY r.updatedAt ASC
    """)
    List<BroadcastRecipient> findRetryCandidates(@Param("broadcastId") Long broadcastId, Pageable pageable);

    long countByBroadcastIdAndStatus(Long broadcastId, BroadcastRecipientStatus status);
}