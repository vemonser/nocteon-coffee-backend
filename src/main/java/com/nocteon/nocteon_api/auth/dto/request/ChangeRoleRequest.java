package com.nocteon.nocteon_api.auth.dto.request;

import com.nocteon.nocteon_api.auth.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeRoleRequest {

    @NotNull(message = "role is required")
    private Role role;
}