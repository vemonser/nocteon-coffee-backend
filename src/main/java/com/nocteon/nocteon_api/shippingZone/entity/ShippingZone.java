package com.nocteon.nocteon_api.shippingZone.entity;

import java.math.BigDecimal;
import java.util.Set;
import java.util.HashSet;

import com.nocteon.nocteon_api.common.entity.SoftDeletableEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "shipping_zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ShippingZone extends SoftDeletableEntity {

    @Column(nullable = false, length = 100)
    private String name; // "Cairo & Giza", "Alexandria & Delta"...

    @Column(name = "shipping_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingCost;

    @ElementCollection
    @CollectionTable(name = "shipping_zone_cities", joinColumns = @JoinColumn(name = "shipping_zone_id"))
    @Column(name = "city", nullable = false)
    @Builder.Default
    private Set<String> cities = new HashSet<>();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}