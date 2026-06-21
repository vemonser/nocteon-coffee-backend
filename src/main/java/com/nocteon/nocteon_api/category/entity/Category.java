package com.nocteon.nocteon_api.category.entity;

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
@Table(name = "categories")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Category extends SoftDeletableEntity  {

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "image_url", nullable = true)
    private String imageUrl;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CategoryTranslation> translations = new ArrayList<>();

}
