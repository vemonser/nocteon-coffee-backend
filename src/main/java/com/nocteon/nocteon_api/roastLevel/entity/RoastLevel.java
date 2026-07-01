package com.nocteon.nocteon_api.roastLevel.entity;

import java.util.ArrayList;
import java.util.List;

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
@Table(name = "roast_levels")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RoastLevel extends SoftDeletableEntity {

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String color;

    @OneToMany(mappedBy = "roastLevel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RoastLevelTranslation> translations = new ArrayList<>();
}