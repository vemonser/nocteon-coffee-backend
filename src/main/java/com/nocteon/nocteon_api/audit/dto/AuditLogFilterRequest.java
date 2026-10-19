package com.nocteon.nocteon_api.audit.dto;

import java.time.Instant;

import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuditLogFilterRequest extends BaseFilterRequest {

    private Long userId;
    private AuditAction action;
    private String entityType;
    private Long entityId;
    private Instant since;
    private Instant until;
}