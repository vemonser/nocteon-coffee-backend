package com.nocteon.nocteon_api.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyEmailRequest {
    @Email(message = "{validation.email.invalid}")
    @NotBlank(message = "{validation.email.notBlank}")
    private String email;

    @NotBlank(message = "{validation.otp.notBlank}")
    @Size(min = 6, max = 6, message = "{validation.otp.size}")
    private String otp;

}
