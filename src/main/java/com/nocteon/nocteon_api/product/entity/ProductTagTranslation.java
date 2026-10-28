package com.nocteon.nocteon_api.product.entity;

import com.nocteon.nocteon_api.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** The display name of a {@link ProductTag} in one language. */
@Entity
@SuperBuilder
@Table(name = "product_tag_translations")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProductTagTranslation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private ProductTag tag;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(nullable = false, length = 100)
    private String name;
}
