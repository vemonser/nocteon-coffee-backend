package com.nocteon.nocteon_api.storeSettings.entity;

import java.math.BigDecimal;

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
@Table(name = "store_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StoreSettings extends BaseEntity {

    @Column(name = "free_shipping_threshold", precision = 10, scale = 2)
    private BigDecimal freeShippingThreshold;

    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private String currency = "EGP";

    @Column(name = "is_free_shipping_active", nullable = false)
    @Builder.Default
    private Boolean isFreeShippingActive = true;
}