package com.nocteon.nocteon_api.product.entity;

import com.nocteon.nocteon_api.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "product_translations")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProductTranslation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(nullable = false)
    private String name;

    @Column(name = "short_description", columnDefinition = "TEXT")
    private String shortDescription;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** The long-form story, per language (the product page's editorial block). */
    @Column(columnDefinition = "TEXT")
    private String story;

    /** Search/social title for this language. The public response falls back to {@code name}. */
    @Column(name = "meta_title", length = 200)
    private String metaTitle;

    /** Search/social description; the public response falls back to {@code shortDescription}. */
    @Column(name = "meta_description", length = 500)
    private String metaDescription;
}