package com.nocteon.nocteon_api.auth.dto.request;

import com.nocteon.nocteon_api.auth.enums.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUpdateUserRequest {
    private String username;
    private String email;
    private Role role;
    private String firstName;
    private String lastName;
    private Boolean isActive;
}