package com.nocteon.nocteon_api.auth.entity;

import com.nocteon.nocteon_api.auth.enums.Provider;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "user_linked_accounts")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class UserLinkedAccount extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;
    @Column(name = "provider_id", nullable = false)
    private String providerId;

}
