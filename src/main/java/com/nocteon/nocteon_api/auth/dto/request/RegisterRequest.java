package com.nocteon.nocteon_api.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "{validation.firstName.notBlank}")
    @Size(min = 2, max = 50, message = "{validation.firstName.size}")
    @Pattern(regexp = "^[a-zA-Z\\u0600-\\u06FF]+$", message = "{validation.firstName.pattern}")
    private String firstName;

    @NotBlank(message = "{validation.lastName.notBlank}")
    @Size(min = 2, max = 50, message = "{validation.lastName.size}")
    @Pattern(regexp = "^[a-zA-Z\\u0600-\\u06FF]+$", message = "{validation.lastName.pattern}")
    private String lastName;

    @NotBlank(message = "{validation.username.notBlank}")
    @Size(min = 2, max = 50, message = "{validation.username.size}")
    @Pattern(regexp = "^[a-z0-9_]+$", message = "{validation.username.pattern}")
    private String username;

    @NotBlank(message = "{validation.phone.notBlank}")
    @Size(min = 11, max = 15, message = "{validation.phone.size}")
    @Pattern(regexp = "^[0-9]+$", message = "{validation.phone.pattern}")
    private String phone;

    @Email(message = "{validation.email.invalid}")
    @NotBlank(message = "{validation.email.notBlank}")
    private String email;

    @NotBlank(message = "{validation.password.notBlank}")
    @Size(min = 8, message = "{validation.password.size}")
    private String password;

}
