package com.nocteon.nocteon_api.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {


    @NotBlank(message = "{validation.password.notBlank}")
    @Size(min = 8, message = "{validation.password.size}")
    private String oldPassword;

    @NotBlank(message = "{validation.password.notBlank}")
    @Size(min = 8, message = "{validation.password.size}")
    private String newPassword;
    @NotBlank(message = "{validation.password.notBlank}")
    @Size(min = 8, message = "{validation.password.size}")
    private String confirmPassword;
}
