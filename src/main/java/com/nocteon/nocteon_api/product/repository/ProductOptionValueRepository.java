package com.nocteon.nocteon_api.product.repository;

import com.nocteon.nocteon_api.product.entity.ProductOptionValue;

import io.lettuce.core.dynamic.annotation.Param;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductOptionValueRepository extends JpaRepository<ProductOptionValue, Long> {

    boolean existsByOptionIdAndSlug(Long optionId, String slug);

    List<ProductOptionValue> findByOptionIdOrderBySortOrder(Long optionId);

    @Query("""
            SELECT COUNT(DISTINCT ov.option.id)
            FROM ProductOptionValue ov
            WHERE ov.id IN :ids
            """)
    long countDistinctOptionsByValueIds(@Param("ids") List<Long> ids);
}