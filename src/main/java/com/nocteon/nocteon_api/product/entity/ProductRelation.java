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

/**
 * FE-11: one "You may also like" entry.
 *
 * <p>A row rather than an ordered join collection: the order is the admin's, and rewriting it has
 * to be a replace-all (delete the product's rows, insert them in the new order) — Hibernate's
 * ordered collection updates positions in place and collides on the primary key as soon as two
 * entries swap.
 */
@Entity
@SuperBuilder
@Table(name = "product_relations")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProductRelation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_product_id", nullable = false)
    private Product relatedProduct;

    /** Zero-based display order within the owning product. */
    @Column(nullable = false)
    @Builder.Default
    private int position = 0;
}
