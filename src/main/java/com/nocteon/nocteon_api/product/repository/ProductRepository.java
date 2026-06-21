package com.nocteon.nocteon_api.product.repository;

import java.util.List;
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
                        LEFT JOIN FETCH p.translations t
                        LEFT JOIN FETCH p.media m
                        WHERE t.language = :language
                        AND (:categorySlug IS NULL OR p.category.slug = :categorySlug)
                        AND (:originSlug IS NULL OR p.origin.slug = :originSlug)
                        AND (:productType IS NULL OR p.productType = :productType)
                        AND (:featured IS NULL OR p.featured = :featured)
                        """)
        Page<Product> findAllWithFilters(
                        @Param("language") String language,
                        @Param("categorySlug") String categorySlug,
                        @Param("originSlug") String originSlug,
                        @Param("productType") ProductType productType,
                        @Param("featured") Boolean featured,
                        Pageable pageable);

        @Query("""
                        SELECT DISTINCT p FROM Product p
                        LEFT JOIN FETCH p.translations t
                        LEFT JOIN FETCH p.variants v
                        LEFT JOIN FETCH p.media
                        LEFT JOIN FETCH p.tastingNotes
                        LEFT JOIN FETCH p.pairings
                        WHERE p.slug = :slug
                        AND t.language = :language
                        """)
        Optional<Product> findBySlugAndLanguage(
                        @Param("slug") String slug,
                        @Param("language") String language);

 

        @Query("""
                        SELECT DISTINCT p FROM Product p
                        LEFT JOIN FETCH p.translations t
                        LEFT JOIN FETCH p.media
                        WHERE p.category.slug = :categorySlug
                        AND t.language = :language
                        ORDER BY p.id DESC
                        """)
        List<Product> findByCategorySlugAndLanguage(
                        @Param("categorySlug") String categorySlug,
                        @Param("language") String language);

        @Query("""
                        SELECT DISTINCT p FROM Product p
                        LEFT JOIN FETCH p.translations t
                        LEFT JOIN FETCH p.media
                        WHERE p.featured = true
                        AND t.language = :language
                        ORDER BY p.id DESC
                        """)
        List<Product> findFeaturedWithLanguage(@Param("language") String language);

        @Query("""
                        SELECT DISTINCT p FROM Product p
                        LEFT JOIN FETCH p.translations t
                        LEFT JOIN FETCH p.media
                        WHERE p.origin.slug = :originSlug
                        AND t.language = :language
                        ORDER BY p.id DESC
                        """)
        List<Product> findByOriginSlugAndLanguage(
                        @Param("originSlug") String originSlug,
                        @Param("language") String language);

        boolean existsBySlug(String slug);
}
