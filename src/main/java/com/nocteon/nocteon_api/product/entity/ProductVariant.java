package com.nocteon.nocteon_api.product.entity;

import java.math.BigDecimal;

import org.springframework.data.annotation.Version;

import com.nocteon.nocteon_api.common.entity.SoftDeletableEntity;
import com.nocteon.nocteon_api.product.enums.GrindType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
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
@Table(name = "product_variants", indexes = {
        @Index(name = "idx_variant_product", columnList = "product_id")
})
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProductVariant extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(length = 150, nullable = false, unique = true)
    private String sku;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 10, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "weight_grams", precision = 8, scale = 2)
    private BigDecimal weightGrams;

    @Enumerated(EnumType.STRING)
    @Column(name = "grind_type")
    private GrindType grindType;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private int stockQuantity = 0;

    @Version
    private Long version;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    public BigDecimal getSellingPrice() {
        return price;
    }

    public void decreaseStock(int quantity) {
        if (this.stockQuantity < quantity) {
            throw new IllegalArgumentException("Insufficient stock");
        }
        this.stockQuantity -= quantity;
    }
}