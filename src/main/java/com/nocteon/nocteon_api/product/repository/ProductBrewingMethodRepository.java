package com.nocteon.nocteon_api.product.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.product.entity.ProductBrewingMethod;

public interface ProductBrewingMethodRepository extends JpaRepository<ProductBrewingMethod, Long> {
    @Query("""
            SELECT pbm FROM ProductBrewingMethod pbm
            LEFT JOIN FETCH pbm.product p
            WHERE pbm.brewingMethod.slug = :slug
            """)
    Page<ProductBrewingMethod> findByBrewingMethodSlug(
            @Param("slug") String slug,
            Pageable pageable);

    List<ProductBrewingMethod> findByProductId(Long productId);

    void deleteByProductId(Long productId);
}