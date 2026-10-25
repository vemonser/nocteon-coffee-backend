package com.nocteon.nocteon_api.audit.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nocteon.nocteon_api.audit.entity.AuditLog;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(AuditAction action, Long userId, String userEmail, String entityType, Long entityId, String entitySlug,
                    String ipAddress, String userAgent, Map<String, Object> oldValues, Map<String, Object> newValues,
                    Map<String, Object> metadata, boolean success, String errorMessage) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            AuditLog log = AuditLog.builder()
                    .userId(userId)
                    .userEmail(userEmail)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .entitySlug(entitySlug)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .oldValues(oldValues != null ? mapper.writeValueAsString(oldValues) : null)
                    .newValues(newValues != null ? mapper.writeValueAsString(newValues) : null)
                    .metadata(metadata != null ? mapper.writeValueAsString(metadata) : null)
                    .success(success)
                    .errorMessage(errorMessage)
                    .build();
            auditLogRepository.save(log);
        } catch (Exception e) {
            log.error("Failed to serialize audit log: {}", e.getMessage());
        }
    }

    @Transactional
    public void logSuccess(AuditAction action, Long userId, String userEmail, String entityType, Long entityId, String entitySlug,
                           String ipAddress, String userAgent, Map<String, Object> oldValues, Map<String, Object> newValues,
                           Map<String, Object> metadata) {
        log(action, userId, userEmail, entityType, entityId, entitySlug, ipAddress, userAgent, oldValues, newValues, metadata, true, null);
    }

    @Transactional
    public void logFailure(AuditAction action, Long userId, String userEmail, String entityType, Long entityId, String entitySlug,
                           String ipAddress, String userAgent, Map<String, Object> oldValues, Map<String, Object> newValues,
                           Map<String, Object> metadata, String errorMessage) {
        log(action, userId, userEmail, entityType, entityId, entitySlug, ipAddress, userAgent, oldValues, newValues, metadata, false, errorMessage);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> findAllFiltered(Long userId, AuditAction action, String entityType, Long entityId,
                                          Instant since, Instant until, Pageable pageable) {
        // Avoid PostgreSQL "could not determine data type of parameter" for null Instant bind values.
        Instant s = (since == null) ? Instant.EPOCH : since;
        Instant u = (until == null) ? Instant.parse("2100-01-01T00:00:00Z") : until;
        return auditLogRepository.findAllFiltered(userId, action, entityType, entityId, s, u, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<AuditLog> findLatestByEntity(String entityType, Long entityId) {
        return auditLogRepository.findByEntity(entityType, entityId).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<AuditLog> findLatestByUser(Long userId) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findByEntity(String entityType, Long entityId) {
        return auditLogRepository.findByEntity(entityType, entityId);
    }
}