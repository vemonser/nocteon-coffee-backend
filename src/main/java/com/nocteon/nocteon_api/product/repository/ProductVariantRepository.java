package com.nocteon.nocteon_api.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.product.entity.ProductOptionValue;
import com.nocteon.nocteon_api.product.entity.ProductVariant;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    // في ProductVariantRepository
    // في ProductVariantRepository
    @Query("""
            SELECT DISTINCT ov
            FROM ProductVariant v
            JOIN v.optionValues ov
            WHERE v.product.id IN :productIds
            AND v.isActive = true
            """)
    List<ProductOptionValue> findDistinctOptionValuesByProductIds(@Param("productIds") List<Long> productIds);

    List<ProductVariant> findByProductId(Long productId);

    Optional<ProductVariant> findBySku(String sku);

    boolean existsBySku(String sku);

    void deleteByProductId(Long productId);
}
