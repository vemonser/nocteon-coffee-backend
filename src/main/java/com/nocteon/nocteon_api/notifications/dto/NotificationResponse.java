package com.nocteon.nocteon_api.notifications.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

import com.nocteon.nocteon_api.notifications.enums.NotificationType;

@Getter
@Builder
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private String link;
    private boolean isRead;
    private Instant createdAt;
}
