package com.nocteon.nocteon_api.farm.entity;

import java.util.ArrayList;
import java.util.List;

import com.nocteon.nocteon_api.common.entity.SoftDeletableEntity;
import com.nocteon.nocteon_api.origin.entity.Origin;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "farms")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Farm extends SoftDeletableEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_id", nullable = false)
    private Origin origin;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "image_url")
    private String imageUrl;

    @OneToMany(mappedBy = "farm", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<FarmTranslation> translations = new ArrayList<>();
}
