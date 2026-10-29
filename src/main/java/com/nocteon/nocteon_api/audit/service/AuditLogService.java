package com.nocteon.nocteon_api.audit.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nocteon.nocteon_api.audit.dto.AuditLogFilterRequest;
import com.nocteon.nocteon_api.audit.entity.AuditLog;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    /** The upper bound for "no end date": any recorded row predates it. */
    private static final Instant MAX_INSTANT = Instant.parse("2100-01-01T00:00:00Z");

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

    /**
     * The row for an action that HAPPENED.
     *
     * <p>Joins the caller's transaction (default propagation), so the row and
     * the change it describes commit or roll back together: a call that is
     * refused after this write — or rolled back by an outer transaction — leaves
     * no row claiming the change was made.
     */
    @Transactional
    public void logSuccess(AuditAction action, Long userId, String userEmail, String entityType, Long entityId, String entitySlug,
                           String ipAddress, String userAgent, Map<String, Object> oldValues, Map<String, Object> newValues,
                           Map<String, Object> metadata) {
        log(action, userId, userEmail, entityType, entityId, entitySlug, ipAddress, userAgent, oldValues, newValues, metadata, true, null);
    }

    /**
     * The row for an action that was REFUSED.
     *
     * <p>Joins the caller's transaction, like {@link #logSuccess} — and the caller
     * only keeps it because a refusal CHANGES nothing: the refusing service marks
     * its refusal exceptions as {@code noRollbackFor}, so the transaction commits
     * with the refusal row as its only write.
     *
     * <p>That is deliberate. Writing this row in a second transaction
     * ({@code REQUIRES_NEW}) would need a second pooled connection while the first
     * one is still held by the refusing transaction; a burst of concurrent failures
     * — a credential-stuffing run against {@code /login} is the realistic one —
     * could then exhaust the pool with connections waiting on connections. The
     * asymmetry the log needs (a SUCCESS row rolls back with its change) costs
     * nothing here: nothing but this row was written.
     */
    @Transactional
    public void logFailure(AuditAction action, Long userId, String userEmail, String entityType, Long entityId, String entitySlug,
                           String ipAddress, String userAgent, Map<String, Object> oldValues, Map<String, Object> newValues,
                           Map<String, Object> metadata, String errorMessage) {
        log(action, userId, userEmail, entityType, entityId, entitySlug, ipAddress, userAgent, oldValues, newValues, metadata, false, errorMessage);
    }

    /**
     * The audit page's page of rows.
     *
     * <p>Read-only and permission-checked at the controller (`audit:read`).
     *
     * <p>The two text filters are turned into LIKE patterns here, once, so the
     * repository stays a query and the interpretation of "actor" lives in one
     * place: digits are a user id, anything else is a fragment of the email.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> search(AuditLogFilterRequest filter) {
        // Avoid PostgreSQL "could not determine data type of parameter" for null Instant bind values.
        Instant since = filter.getSince() == null ? Instant.EPOCH : filter.getSince();
        Instant until = filter.getUntil() == null ? MAX_INSTANT : filter.getUntil();

        String actor = filter.getActor() == null ? null : filter.getActor().trim();
        Long actorId = null;
        String actorEmailPattern = null;
        if (actor != null && !actor.isEmpty()) {
            Long parsedId = parseActorId(actor);
            if (parsedId != null) {
                // A numeric actor is the id the row recorded, not an email.
                actorId = parsedId;
            } else {
                actorEmailPattern = likeContains(actor);
            }
        }

        String action = filter.getAction() == null ? null : filter.getAction().trim();
        String actionPattern = (action == null || action.isEmpty()) ? null : likeContains(action);

        return auditLogRepository.search(
                actionPattern,
                actorId,
                actorEmailPattern,
                blankToNull(filter.getEntityType()),
                filter.getEntityId(),
                since,
                until,
                stablePageable(filter));
    }

    /**
     * The requested page and sort, with the id appended as a tie-breaker.
     *
     * <p>{@code created_at} is a timestamp with microsecond precision, and rows
     * written by one request can share it. Without the id in the ORDER BY, page
     * 2 can repeat a row page 1 already showed. The direction follows the
     * caller's, so an ascending view is stable too.
     */
    private static Pageable stablePageable(AuditLogFilterRequest filter) {
        Pageable requested = filter.toPageable();
        Sort.Direction direction = "asc".equalsIgnoreCase(filter.getDirection())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(
                requested.getPageNumber(),
                requested.getPageSize(),
                requested.getSort().and(Sort.by(direction, "id")));
    }

    /**
     * The actor id when the filter is a plain user id, otherwise null.
     *
     * <p>A number too large for a Long is not an id anyone holds, so it falls
     * through to the email fragment form instead of throwing: a filter box must
     * never answer a typo with a 500.
     */
    private static Long parseActorId(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A literal `%` or `_` typed in the filter stays a LIKE wildcard.
     *
     * <p>Hibernate renders PostgreSQL `LIKE ... ESCAPE ''` — the escape
     * character is deliberately disabled — so a backslash would be a literal
     * backslash and escaping `_` would break the most useful search on this
     * table ("order_create"). A pattern character is harmless here: the reader
     * is searching a log, and `_` still matches the underscore it typed.
     */
    private static String likeContains(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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