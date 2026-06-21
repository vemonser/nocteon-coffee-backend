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

    private UserPrincipal(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    public static UserPrincipal create(User user) {
        return new UserPrincipal(user, null);
    }

    public static UserPrincipal create(User user, Map<String, Object> attributes) {
        return new UserPrincipal(user, attributes);
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
        return user.getRole().getPermissions().stream()
                .map(p -> new SimpleGrantedAuthority(p.getPermission()))
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
