package com.nocteon.nocteon_api.product.entity;

import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVariety;
import com.nocteon.nocteon_api.common.entity.BaseEntity;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "coffee_details")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CoffeeDetails extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processing_method_id")
    private ProcessingMethod processingMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coffee_variety_id")
    private CoffeeVariety coffeeVariety;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roast_level_id")
    private RoastLevel roastLevel;
    
    private String altitude;

    @Column(name = "harvest_year")
    private String harvestYear;

    @Column(columnDefinition = "TEXT")
    private String story;
}