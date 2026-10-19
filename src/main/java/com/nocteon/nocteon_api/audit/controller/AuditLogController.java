package com.nocteon.nocteon_api.audit.controller;

import jakarta.validation.Valid;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.audit.dto.AuditLogFilterRequest;
import com.nocteon.nocteon_api.audit.dto.AuditLogResponse;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.service.AuditLogService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.PageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getAll(
            @Valid
            @ModelAttribute AuditLogFilterRequest filter,
            Pageable pageable) {
        Page<AuditLogResponse> page = auditLogService.findAllFiltered(
                filter.getUserId(),
                filter.getAction(),
                filter.getEntityType(),
                filter.getEntityId(),
                filter.getSince(),
                filter.getUntil(),
                pageable)
                .map(AuditLogResponse::fromEntity);

        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page), "Audit logs retrieved"));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAuthority('audit:read')")
    public ResponseEntity<ApiResponse<java.util.List<AuditLogResponse>>> getByEntity(
            @PathVariable String entityType,
            @PathVariable Long entityId) {
        var logs = auditLogService.findByEntity(entityType, entityId)
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