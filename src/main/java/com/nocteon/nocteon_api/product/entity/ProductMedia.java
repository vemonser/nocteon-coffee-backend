package com.nocteon.nocteon_api.product.entity;

import com.nocteon.nocteon_api.common.entity.BaseEntity;
import com.nocteon.nocteon_api.product.enums.MediaType;

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
@Table(name = "product_media")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProductMedia extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String url;

    @Column(name = "alt_text")
    private String altText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MediaType type = MediaType.IMAGE;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean isPrimary = false;
}