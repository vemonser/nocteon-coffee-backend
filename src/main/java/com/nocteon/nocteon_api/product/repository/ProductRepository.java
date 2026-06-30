package com.nocteon.nocteon_api.product.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.enums.ProductType;

import io.lettuce.core.dynamic.annotation.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            SELECT DISTINCT p FROM Product p
            LEFT JOIN p.translations t
            LEFT JOIN p.variants v
            WHERE t.language = :language
            AND p.isActive = true
            AND (:search = ''
                 OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
            AND (:categorySlug IS NULL OR p.category.slug = :categorySlug)
            AND (:originSlug IS NULL OR p.origin.slug = :originSlug)
            AND (:productType IS NULL OR p.productType = :productType)
            AND (:featured IS NULL OR p.featured = :featured)
            """)
    Page<Product> findAllPublic(
            @Param("language") String language,
            @Param("search") String search,
            @Param("categorySlug") String categorySlug,
            @Param("originSlug") String originSlug,
            @Param("productType") ProductType productType,
            @Param("featured") Boolean featured,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT p FROM Product p
            LEFT JOIN p.translations t
            WHERE (:search = ''
                   OR  LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
            AND (:categorySlug IS NULL OR p.category.slug = :categorySlug)
            AND (:productType IS NULL OR p.productType = :productType)
            AND (:isActive IS NULL OR p.isActive = :isActive)
            AND (:featured IS NULL OR p.featured = :featured)
            """)
    Page<Product> findAllDashboard(
            @Param("search") String search,
            @Param("categorySlug") String categorySlug,
            @Param("productType") ProductType productType,
            @Param("isActive") Boolean isActive,
            @Param("featured") Boolean featured,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT p FROM Product p
            LEFT JOIN FETCH p.translations t
            LEFT JOIN FETCH p.variants v
            LEFT JOIN FETCH p.media
            LEFT JOIN FETCH p.tastingNotes tn
            LEFT JOIN FETCH tn.translations
            LEFT JOIN FETCH p.pairings pa
            LEFT JOIN FETCH pa.translations
            WHERE p.slug = :slug
            AND t.language = :language
            """)
    Optional<Product> findBySlugAndLanguage(
            @Param("slug") String slug,
            @Param("language") String language);

    boolean existsBySlug(String slug);
}