package com.nocteon.nocteon_api.product.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.product.entity.ProductRelation;

public interface ProductRelationRepository extends JpaRepository<ProductRelation, Long> {

    /**
     * The "You may also like" entries of one product, in the admin's order. The related product is
     * fetched here: the caller always needs its slug, name and picture, and a lazy proxy each
     * would be one query per entry.
     */
    @Query("""
            SELECT r FROM ProductRelation r
            JOIN FETCH r.relatedProduct
            WHERE r.product.id = :productId
            ORDER BY r.position
            """)
    List<ProductRelation> findByProductIdOrderByPosition(@Param("productId") Long productId);

    @Modifying
    @Query("DELETE FROM ProductRelation r WHERE r.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);

    /** The related product slugs of one product, in order — used when duplicating. */
    @Query("""
            SELECT r.relatedProduct.slug FROM ProductRelation r
            WHERE r.product.id = :productId
            ORDER BY r.position
            """)
    List<String> findRelatedSlugs(@Param("productId") Long productId);
}
