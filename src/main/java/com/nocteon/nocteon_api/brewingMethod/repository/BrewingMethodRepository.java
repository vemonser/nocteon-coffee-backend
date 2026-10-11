package com.nocteon.nocteon_api.brewingMethod.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;


public interface BrewingMethodRepository extends JpaRepository<BrewingMethod, Long> {

    @Query("""
            SELECT DISTINCT b FROM BrewingMethod b
            LEFT JOIN b.translations t
            WHERE (COALESCE(:search, '') = ''
                   OR (t.language = :language
                       AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))))
            """)
    Page<BrewingMethod> findAllPublic(
            @Param("search") String search,
            @Param("language") String language,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT b FROM BrewingMethod b
            LEFT JOIN b.translations t
            WHERE (COALESCE(:search, '') = ''
                   OR  LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR  LOWER(b.slug) LIKE LOWER(CONCAT('%', :search, '%'))
                )
            """)
    Page<BrewingMethod> findAllDashboard(
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT b FROM BrewingMethod b
            LEFT JOIN FETCH b.translations
            WHERE b.slug = :slug
            """)
    Optional<BrewingMethod> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("""
            SELECT b FROM BrewingMethod b
            LEFT JOIN FETCH b.translations t
            WHERE b.slug = :slug AND t.language = :language
            """)
    Optional<BrewingMethod> findBySlugAndLanguage(
            @Param("slug") String slug,
            @Param("language") String language);

    Optional<BrewingMethod> findBySlug(String slug);

    List<BrewingMethod> findBySlugIn(List<String> slugs);

    boolean existsBySlug(String slug);
}