package com.nocteon.nocteon_api.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RegisterResponse {
    private Long userId;
    private String email;
    private String message;
}