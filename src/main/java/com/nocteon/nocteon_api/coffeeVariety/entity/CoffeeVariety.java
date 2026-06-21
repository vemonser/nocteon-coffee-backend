package com.nocteon.nocteon_api.coffeeVariety.entity;

import java.util.List;
import java.util.ArrayList;

import com.nocteon.nocteon_api.common.entity.SoftDeletableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "coffee_varieties")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CoffeeVariety extends SoftDeletableEntity {

    @Column(nullable = false, unique = true)
    private String slug;

    @OneToMany(mappedBy = "coffeeVariety", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CoffeeVarietyTranslation> translations = new ArrayList<>();
}