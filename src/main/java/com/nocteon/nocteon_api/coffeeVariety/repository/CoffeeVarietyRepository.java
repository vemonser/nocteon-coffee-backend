package com.nocteon.nocteon_api.coffeeVariety.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVariety;

import io.lettuce.core.dynamic.annotation.Param;

public interface CoffeeVarietyRepository extends JpaRepository<CoffeeVariety, Long> {

    @Query("""
            SELECT DISTINCT c FROM CoffeeVariety c
            LEFT JOIN c.translations t
            WHERE (:search = ''
                   OR (t.language = :language
                       AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))))
            """)
    Page<CoffeeVariety> findAllPublic(
            @Param("search") String search,
            @Param("language") String language,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT c FROM CoffeeVariety c
            LEFT JOIN c.translations t
            WHERE (:search = ''
                   OR  LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR  LOWER(c.slug) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<CoffeeVariety> findAllDashboard(
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT c FROM CoffeeVariety c
            LEFT JOIN FETCH c.translations
            WHERE c.slug = :slug
            """)
    Optional<CoffeeVariety> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("""
            SELECT c FROM CoffeeVariety c
            LEFT JOIN FETCH c.translations t
            WHERE c.slug = :slug AND t.language = :language
            """)
    Optional<CoffeeVariety> findBySlugAndLanguage(
            @Param("slug") String slug,
            @Param("language") String language);

    Optional<CoffeeVariety> findBySlug(String slug);

    List<CoffeeVariety> findBySlugIn(List<String> slugs);

    boolean existsBySlug(String slug);
}