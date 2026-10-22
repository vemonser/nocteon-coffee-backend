package com.nocteon.nocteon_api.broadcast.dto.response;

import java.time.Instant;

import com.nocteon.nocteon_api.broadcast.enums.BroadcastRecipientStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BroadcastRecipientResponse {

    private Long id;
    private String email;
    private BroadcastRecipientStatus status;
    private int attemptCount;
    private String lastError;
    private Instant sentAt;
}
