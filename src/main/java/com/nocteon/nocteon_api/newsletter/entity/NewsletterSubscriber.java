package com.nocteon.nocteon_api.newsletter.entity;

import com.nocteon.nocteon_api.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "newsletter_subscribers")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterSubscriber extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Builder.Default
    @Column(nullable = false)
    private boolean subscribed = true;
}