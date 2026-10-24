package com.nocteon.nocteon_api.notifications.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class NotificationResponse {
    private Long id;
    private String type;
    private Object params;
    private String entityType;
    private Long entityId;
    private Instant createdAt;
    private Instant readAt;
}
