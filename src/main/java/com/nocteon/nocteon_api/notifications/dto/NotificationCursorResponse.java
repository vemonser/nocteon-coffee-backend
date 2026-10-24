package com.nocteon.nocteon_api.notifications.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationCursorResponse {
    private List<NotificationResponse> content;
    private String nextCursor;
    private boolean hasMore;
}
