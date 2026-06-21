package com.nocteon.nocteon_api.product.entity;

import java.math.BigDecimal;

import com.nocteon.nocteon_api.common.entity.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "product_variants")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProductVariant extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(precision = 8, scale = 2)
    private BigDecimal weight;

    @Column(name = "grind_type")
    private String grindType;

    @Column(nullable = false)
    @Builder.Default
    private int stock = 0;

    @Column(precision = 5, scale = 2)
    private BigDecimal discount;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}