package com.nocteon.nocteon_api.promoCode.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

import org.hibernate.annotations.BatchSize;

import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.common.entity.SoftDeletableEntity;
import com.nocteon.nocteon_api.promoCode.enums.PromoCodeDiscountType;
import com.nocteon.nocteon_api.promoCode.enums.PromoScopeType;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "promo_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PromoCode extends SoftDeletableEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private PromoCodeDiscountType discountType;

    @Column(name = "discount_value", precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private PromoScopeType scopeType;

    @Column(name = "max_total_redemptions")
    private Integer maxTotalRedemptions;

    @Column(name = "max_redemptions_per_user", nullable = false)
    @Builder.Default
    private Integer maxRedemptionsPerUser = 1;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @ManyToMany
    @JoinTable(
        name = "promo_code_categories",
        joinColumns = @JoinColumn(name = "promo_code_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @BatchSize(size = 30)
    @Builder.Default
    private List<Category> categories = new ArrayList<>();
}