package com.nocteon.nocteon_api.product.entity;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.annotation.Version;

import com.nocteon.nocteon_api.common.entity.SoftDeletableEntity;

import org.hibernate.annotations.BatchSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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

    /**
     * FE-15: the scannable GTIN / EAN / UPC of this variant. Optional, and unique when set —
     * blank input is stored as {@code null}, so "no barcode" has a single representation and a
     * partial unique index (V20) still allows any number of variants without one.
     */
    @Column(length = 64)
    private String barcode;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 10, scale = 2)
    private BigDecimal compareAtPrice;

    @ManyToMany
    @JoinTable(name = "variant_option_values", joinColumns = @JoinColumn(name = "variant_id"), inverseJoinColumns = @JoinColumn(name = "option_value_id"))
    @BatchSize(size = 30)
    private Set<ProductOptionValue> optionValues = new HashSet<>();

    /**
     * The photo this variant shows instead of the product gallery. {@code null} means "use the
     * product gallery", resolved by the storefront — never copied from the media table, so a
     * cover change still reaches a variant that never had a photo of its own.
     */
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private int stockQuantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private int reservedQuantity = 0;

    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "reorder_threshold", nullable = false)
    @Builder.Default
    private int reorderThreshold = 5;

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

    public int getAvailableStock() {
        return Math.max(0, this.stockQuantity - this.reservedQuantity);
    }

    /**
     * Shared low-stock rule: an enabled variant whose stock is at or below its
     * reorder threshold. Reused by the dashboard Low stock card and the
     * products list aggregate.
     */
    public boolean isLowStock() {
        return this.isActive && this.stockQuantity <= this.reorderThreshold;
    }

    public void reserve(int quantity) {
        if (getAvailableStock() < quantity) {
            throw new IllegalArgumentException("Insufficient stock");
        }
        this.reservedQuantity += quantity;
    }

    public void release(int quantity) {
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
    }

    public void consume(int quantity) {
        if (this.stockQuantity < quantity || this.reservedQuantity < quantity) {
            throw new IllegalArgumentException("Insufficient stock");
        }
        this.stockQuantity -= quantity;
        this.reservedQuantity -= quantity;
    }

    public void restock(int quantity) {
        this.stockQuantity += quantity;
    }
}