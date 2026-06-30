package com.nocteon.nocteon_api.origin.entity;

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
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "origins")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Origin extends SoftDeletableEntity {
    
    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(name = "image_url")
    private String imageUrl;

    @OneToMany(mappedBy = "origin", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @BatchSize(size = 30)
    private List<OriginTranslation> translations = new ArrayList<>();
}
