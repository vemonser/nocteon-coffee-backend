package com.nocteon.nocteon_api.brewingMethod.entity;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

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
@Table(name = "brewing_methods")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class BrewingMethod extends SoftDeletableEntity {

    @Column(nullable = false, unique = true)
    private String slug;

    @OneToMany(mappedBy = "brewingMethod", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @BatchSize(size = 30)
    private List<BrewingMethodTranslation> translations = new ArrayList<>();
}