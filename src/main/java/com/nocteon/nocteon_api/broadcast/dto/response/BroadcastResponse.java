package com.nocteon.nocteon_api.broadcast.dto.response;

import java.time.Instant;

import com.nocteon.nocteon_api.broadcast.enums.BroadcastStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BroadcastResponse {
    private Long id;
    private String subject;
    private String content;
    private BroadcastStatus status;
    private int totalRecipients;
    private int sentCount;
    private int failedCount;
    private Instant createdAt;
}
