package com.nocteon.nocteon_api.auth.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.common.entity.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class User extends BaseEntity {
    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;
    private String password;

    @Enumerated(EnumType.STRING)
    private Provider provider;

    @Column(name = "provider_id")
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Builder.Default
    private boolean enabled = false;

    @Column(name = "failed_login_attempts")
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_failed_login")
    private Instant lastFailedLogin;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserProfile profile;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<UserLinkedAccount> linkedAccounts = new ArrayList<>();

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "is_subscribed", nullable = false)
    @Builder.Default
    private boolean subscribed = true;

    public boolean isLocked() {
        if (lockedUntil == null)
            return false;
        return Instant.now().isBefore(lockedUntil);
    }

    public void incrementFailedLogin() {
        this.failedLoginAttempts++;
        this.lastFailedLogin = Instant.now();
        if (this.failedLoginAttempts >= 5) {
            this.lockedUntil = Instant.now().plus(30, ChronoUnit.MINUTES);
        }
    }

    public void resetFailedLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastFailedLogin = null;
    }

}
