package com.nocteon.nocteon_api.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private String accessToken;
    private Long expiresIn;
    private UserResponse user;

}
