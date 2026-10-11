package com.nocteon.nocteon_api.product.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.product.entity.ProductOption;


public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    boolean existsBySlug(String slug);

    @Query("""
            SELECT DISTINCT o FROM ProductOption o
            LEFT JOIN FETCH o.translations
            WHERE o.slug = :slug
            """)
    Optional<ProductOption> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("""
            SELECT o FROM ProductOption o
            LEFT JOIN o.translations t
            WHERE (:search IS NULL OR COALESCE(:search, '') = ''
                   OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY o.sortOrder
            """)
    Page<ProductOption> findAllDashboard(@Param("search") String search, Pageable pageable);
}