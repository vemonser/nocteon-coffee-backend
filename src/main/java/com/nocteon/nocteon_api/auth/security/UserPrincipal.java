package com.nocteon.nocteon_api.auth.security;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.nocteon.nocteon_api.auth.entity.User;

import lombok.Getter;

@Getter
public class UserPrincipal implements UserDetails, OAuth2User {

    private final User user;
    private final Map<String, Object> attributes;
    private final java.util.List<String> permissions;

    private UserPrincipal(User user, Map<String, Object> attributes, java.util.List<String> permissions) {
        this.user = user;
        this.attributes = attributes;
        this.permissions = permissions != null ? permissions : java.util.List.of();
    }

    public static UserPrincipal create(User user) {
        java.util.List<String> perms = java.util.List.of();
        com.nocteon.nocteon_api.auth.service.RoleService rs = com.nocteon.nocteon_api.auth.service.RoleService.getInstance();
        if (rs != null) {
            perms = new java.util.ArrayList<>(rs.getPermissions(user.getRole()));
        } else {
            // fallback to enum
            try {
                com.nocteon.nocteon_api.auth.enums.Role r = com.nocteon.nocteon_api.auth.enums.Role.valueOf(user.getRole());
                perms = r.getPermissions().stream().map(com.nocteon.nocteon_api.common.enums.Permission::getPermission).toList();
            } catch (Exception ignored) {}
        }
        return new UserPrincipal(user, null, perms);
    }

    public static UserPrincipal create(User user, Map<String, Object> attributes) {
        java.util.List<String> perms = java.util.List.of();
        com.nocteon.nocteon_api.auth.service.RoleService rs = com.nocteon.nocteon_api.auth.service.RoleService.getInstance();
        if (rs != null) {
            perms = new java.util.ArrayList<>(rs.getPermissions(user.getRole()));
        } else {
            try {
                com.nocteon.nocteon_api.auth.enums.Role r = com.nocteon.nocteon_api.auth.enums.Role.valueOf(user.getRole());
                perms = r.getPermissions().stream().map(com.nocteon.nocteon_api.common.enums.Permission::getPermission).toList();
            } catch (Exception ignored) {}
        }
        return new UserPrincipal(user, attributes, perms);
    }

    // ========== OAuth2User ==========
    @Override
    public Map<String, Object> getAttributes() {
        return attributes != null ? attributes : Map.of();
    }

    @Override
    public String getName() {
        return user.getEmail();
    }

    // ========== UserDetails ==========

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !user.isLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return user.getId();
    }

}
