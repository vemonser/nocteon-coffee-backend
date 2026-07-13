package com.nocteon.nocteon_api.auth.dto.response;

import java.time.Instant;
import java.util.List;

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
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private Role role;
    private boolean enabled;
    // @JsonProperty("isActive")
    private boolean isActive;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private Instant createdAt;
    private Instant lastActiveAt;
    private List<String> permissions;
}
