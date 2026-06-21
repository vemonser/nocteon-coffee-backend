package com.nocteon.nocteon_api.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResult {
    private AuthResponse authResponse;
    private String rawRefreshToken;
}