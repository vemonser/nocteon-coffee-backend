package com.nocteon.nocteon_api.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRoleRequest {
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,31}$")
    private String name;

    private String description;

    private String cloneFrom;
}
