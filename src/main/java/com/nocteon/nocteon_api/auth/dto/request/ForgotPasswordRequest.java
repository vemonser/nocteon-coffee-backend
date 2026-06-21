package com.nocteon.nocteon_api.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ForgotPasswordRequest {
    @Email(message = "{validation.email.invalid}")
    @NotBlank(message = "{validation.email.notBlank}")
    private String email;
}
