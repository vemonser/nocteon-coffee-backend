package com.nocteon.nocteon_api.mail.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.mail.entity.EmailLog;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    Optional<EmailLog> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT e FROM EmailLog e WHERE e.status IN ('PENDING', 'FAILED') " +
           "AND e.attemptCount < 5 ORDER BY e.updatedAt ASC")
    List<EmailLog> findRetryCandidates(Pageable pageable);
}