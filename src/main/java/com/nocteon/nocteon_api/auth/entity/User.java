package com.nocteon.nocteon_api.auth.entity;

import java.time.Duration;
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

    @Column(name = "role", nullable = false, length = 64)
    private String role;

    @Builder.Default
    private boolean enabled = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

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
    @org.hibernate.annotations.BatchSize(size = 30)
    private List<UserLinkedAccount> linkedAccounts = new ArrayList<>();

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "is_subscribed", nullable = false)
    @Builder.Default
    private boolean subscribed = true;

    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private int tokenVersion = 0;

    @Column(name = "auth_version", nullable = false)
    @Builder.Default
    private int authVersion = 0;

    public boolean isLocked() {
        if (lockedUntil == null)
            return false;
        return Instant.now().isBefore(lockedUntil);
    }

    public void incrementFailedLogin(int maxAttempts, Duration lockoutDuration) {
        this.failedLoginAttempts++;
        this.lastFailedLogin = Instant.now();
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plus(lockoutDuration);
        }
    }

    public void resetFailedLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastFailedLogin = null;
    }

    public void incrementTokenVersion() {
        this.tokenVersion++;
    }

    public void incrementAuthVersion() {
        this.authVersion++;
    }
}