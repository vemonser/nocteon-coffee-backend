package com.nocteon.nocteon_api.audit.dto;

import java.time.Instant;

import com.nocteon.nocteon_api.audit.entity.AuditLog;
import com.nocteon.nocteon_api.audit.enums.AuditAction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One audit row, as the dashboard reads it.
 *
 * <p>{@code oldValues}, {@code newValues} and {@code metadata} are returned as
 * the STORED JSON TEXT — verbatim, not parsed and re-serialised. Re-serialising
 * was lossy: a value that failed to parse came back as {@code null}, and even a
 * valid one lost its key order and its number spelling (1.10 became 1.1). The
 * audit page pretty-prints the text for reading and shows the same text on
 * copy, so what an auditor sees is exactly what was recorded.
 *
 * <p>Only columns that exist on {@code public.audit_logs} are exposed. The actor is
 * {@code userId} + {@code userEmail}, and both are recorded when an authenticated admin
 * performs the action: the page names the actor by the e-mail and falls back to
 * {@code #id}. A genuine system action records neither, and only then does the page show
 * "System". There is still no username column — the e-mail is the identity the row keeps.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;
    private Long userId;
    private String userEmail;
    private AuditAction action;
    private String entityType;
    private Long entityId;
    private String entitySlug;
    private String ipAddress;
    private String userAgent;
    /** Raw stored JSON, or null when the row recorded no old values. */
    private String oldValues;
    /** Raw stored JSON, or null when the row recorded no new values. */
    private String newValues;
    /** Raw stored JSON, or null when the row recorded no metadata. */
    private String metadata;
    private boolean success;
    private String errorMessage;
    private Instant createdAt;

    public static AuditLogResponse fromEntity(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .userEmail(log.getUserEmail())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .entitySlug(log.getEntitySlug())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .oldValues(log.getOldValues())
                .newValues(log.getNewValues())
                .metadata(log.getMetadata())
                .success(log.isSuccess())
                .errorMessage(log.getErrorMessage())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
