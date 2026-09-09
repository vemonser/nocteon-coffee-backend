package com.nocteon.nocteon_api.auth.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class EmailStatusResponse {
    private boolean exists;
    private boolean verified;
}