package com.nocteon.nocteon_api.audit.controller;

import jakarta.validation.Valid;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.audit.dto.AuditLogFilterRequest;
import com.nocteon.nocteon_api.audit.dto.AuditLogResponse;
import com.nocteon.nocteon_api.audit.service.AuditLogService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * The audit trail, for the dashboard's Settings area.
 *
 * <p>Read-only by design: the table is the record of what happened, so there is
 * no create, update or delete here — a mutable audit log is not an audit log.
 *
 * <p>Every endpoint needs {@code audit:read}; the role catalogue already grants
 * it to ADMIN and MODERATOR, and the server stays the source of truth (the page
 * hides itself without the permission, but the 403 here is the real check).
 */
@RestController
@RequestMapping("/api/dashboard/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * GET /api/dashboard/audit-logs
     *
     * <p>Params: {@code page} (0-based), {@code size} (1..100), {@code sort},
     * {@code direction}, plus the optional {@code action} (contains, case
     * insensitive), {@code actor} (user id or email fragment), {@code since} /
     * {@code until} (ISO instants, inclusive) and {@code entityType} /
     * {@code entityId}.
     *
     * <p>Response: {@code ApiResponse<PageResponse<AuditLogResponse>>} — the
     * envelope every dashboard list uses.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getAll(
            @Valid @ModelAttribute AuditLogFilterRequest filter) {
        Page<AuditLogResponse> page = auditLogService.search(filter).map(AuditLogResponse::fromEntity);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page), "Audit logs retrieved"));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAuthority('audit:read')")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getByEntity(
            @PathVariable String entityType,
            @PathVariable Long entityId) {
        List<AuditLogResponse> logs = auditLogService.findByEntity(entityType, entityId)
                .stream()
                .map(AuditLogResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(logs, "Entity audit logs retrieved"));
    }

    @GetMapping("/user/{userId}/latest")
    @PreAuthorize("hasAuthority('audit:read')")
    public ResponseEntity<ApiResponse<AuditLogResponse>> getLatestByUser(
            @PathVariable Long userId) {
        return auditLogService.findLatestByUser(userId)
                .map(log -> ResponseEntity.ok(ApiResponse.success(AuditLogResponse.fromEntity(log), "Latest audit log retrieved")))
                .orElse(ResponseEntity.notFound().build());
    }
}
