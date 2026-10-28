package com.nocteon.nocteon_api.product.entity;

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

/**
 * FE-10: a merchandising label a product can carry ("New arrivals", "Gift boxes").
 *
 * <p>Category answers "what is it" and stays single-valued; a tag answers "why would I look for
 * it" and a product holds several. The slug is the identity the product form, the list filter and
 * the public API share, so a rename never rewrites it.
 */
@Entity
@SuperBuilder
@Table(name = "product_tags")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProductTag extends SoftDeletableEntity {

    @Column(nullable = false, unique = true)
    private String slug;

    @OneToMany(mappedBy = "tag", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @BatchSize(size = 30)
    private List<ProductTagTranslation> translations = new ArrayList<>();
}
