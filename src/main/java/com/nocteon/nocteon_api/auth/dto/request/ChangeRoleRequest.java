package com.nocteon.nocteon_api.auth.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeRoleRequest {

    @NotNull(message = "role is required")
    private String role;
}