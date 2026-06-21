package com.nocteon.nocteon_api.auth.entity;

import java.time.Instant;

import com.nocteon.nocteon_api.auth.enums.VerificationType;
import com.nocteon.nocteon_api.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "verification_codes")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class VerificationCode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "code_hashed", nullable = false)
    private String codeHashed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationType type;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    private boolean used = false;
}
