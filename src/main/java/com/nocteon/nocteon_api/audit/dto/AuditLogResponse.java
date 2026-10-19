package com.nocteon.nocteon_api.audit.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nocteon.nocteon_api.audit.entity.AuditLog;
import com.nocteon.nocteon_api.audit.enums.AuditAction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private Map<String, Object> oldValues;
    private Map<String, Object> newValues;
    private Map<String, Object> metadata;
    private boolean success;
    private String errorMessage;
    private Instant createdAt;

    public static AuditLogResponse fromEntity(AuditLog log) {
        ObjectMapper mapper = new ObjectMapper();
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
                .oldValues(parseJson(log.getOldValues(), mapper))
                .newValues(parseJson(log.getNewValues(), mapper))
                .metadata(parseJson(log.getMetadata(), mapper))
                .success(log.isSuccess())
                .errorMessage(log.getErrorMessage())
                .createdAt(log.getCreatedAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}