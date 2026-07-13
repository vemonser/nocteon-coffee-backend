package com.nocteon.nocteon_api.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.enums.ProductType;

import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("""
            SELECT p FROM Product p
            WHERE p.category.slug = :slug
            AND p.isActive = true
            """)
    Page<Product> findByCategorySlugPublic(@Param("slug") String slug, Pageable pageable);

    @Query("""
            SELECT p FROM Product p
            WHERE p.farm.slug = :slug
            AND p.isActive = true
            """)
    Page<Product> findByFarmSlugPublic(@Param("slug") String slug, Pageable pageable);

    @Query("""
            SELECT p FROM Product p
            WHERE p.origin.slug = :slug
            AND p.isActive = true
            """)
    Page<Product> findByOriginSlugPublic(@Param("slug") String slug, Pageable pageable);

    @Query("""
                SELECT DISTINCT p FROM Product p
                JOIN p.translations t
                WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(p.slug) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    List<Product> searchProducts(@Param("query") String query, Pageable pageable);

    @Query("""
            SELECT DISTINCT p FROM Product p
            LEFT JOIN p.translations t
            LEFT JOIN p.variants v
            LEFT JOIN p.category c
            LEFT JOIN p.origin o
            WHERE t.language = :language
            AND p.isActive = true
            AND (:search IS NULL OR :search = ''
                 OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
            AND (:categorySlug IS NULL OR c.slug = :categorySlug)
            AND (:originSlug IS NULL OR o.slug = :originSlug)
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
            LEFT JOIN p.category c
            WHERE (:search IS NULL OR :search = ''
                   OR  LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
            AND (:categorySlug IS NULL OR c.slug = :categorySlug)
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
            SELECT DISTINCT p
            FROM Product p
            LEFT JOIN FETCH p.translations t
            WHERE p.slug = :slug
            AND t.language = :language
            """)
    Optional<Product> findBySlugAndLanguage(
            @Param("slug") String slug,
            @Param("language") String language);

    @Query("""
                SELECT DISTINCT p FROM Product p
                LEFT JOIN FETCH p.translations
                LEFT JOIN FETCH p.media
                LEFT JOIN FETCH p.variants
                WHERE p.id IN :ids
            """)
    List<Product> findAllByIdInWithDetails(@Param("ids") List<Long> ids);

    Optional<Product> findBySlug(String slug);

    List<Product> findBySlugIn(List<String> slugs);

    boolean existsBySlug(String slug);
}
