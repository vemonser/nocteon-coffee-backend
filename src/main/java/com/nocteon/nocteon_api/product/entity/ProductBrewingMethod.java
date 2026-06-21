package com.nocteon.nocteon_api.product.entity;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;
import com.nocteon.nocteon_api.common.entity.BaseEntity;

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
@Table(name = "product_brewing_methods")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProductBrewingMethod extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brewing_method_id", nullable = false)
    private BrewingMethod brewingMethod;

    @Column(nullable = false)
    @Builder.Default
    private int score = 0;
}