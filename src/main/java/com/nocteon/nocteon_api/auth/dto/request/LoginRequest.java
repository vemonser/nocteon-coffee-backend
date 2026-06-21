package com.nocteon.nocteon_api.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "{validation.identifier.notBlank}")
    private String identifier; 

    @NotBlank(message = "{validation.password.notBlank}")
    private String password;

}
