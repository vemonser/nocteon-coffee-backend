package com.nocteon.nocteon_api.auth.dto.response;

import java.util.List;

import com.nocteon.nocteon_api.auth.enums.Role;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {
    private Long id;
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private Role role;
    private List<String> permissions;
}
