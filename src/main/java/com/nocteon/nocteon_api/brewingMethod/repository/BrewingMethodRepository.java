package com.nocteon.nocteon_api.brewingMethod.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;

import io.lettuce.core.dynamic.annotation.Param;

public interface BrewingMethodRepository extends JpaRepository<BrewingMethod, Long> {

        @Query("""
                        SELECT DISTINCT b FROM BrewingMethod b
                        LEFT JOIN FETCH b.translations t
                        WHERE t.language = :language
                        AND (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
                        """)
        Page<BrewingMethod> findAllPublic(
                        @Param("language") String language,
                        @Param("search") String search,
                        Pageable pageable);

        @Query("""
                        SELECT DISTINCT b FROM BrewingMethod b
                        LEFT JOIN FETCH b.translations t
                        WHERE (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
                        """)
        Page<BrewingMethod> findAllDashboard(
                        @Param("search") String search,
                        Pageable pageable);

        @Query("SELECT b FROM BrewingMethod b LEFT JOIN FETCH b.translations WHERE b.slug = :slug")
        Optional<BrewingMethod> findBySlugWithTranslations(@Param("slug") String slug);

        @Query("SELECT b FROM BrewingMethod b LEFT JOIN FETCH b.translations t WHERE b.slug = :slug AND t.language = :language")
        Optional<BrewingMethod> findBySlugAndLanguage(@Param("slug") String slug, @Param("language") String language);

        Optional<BrewingMethod> findBySlug(@Param("slug") String slug);
        
        List<BrewingMethod> findBySlugIn(List<String> slugs);

        boolean existsBySlug(String slug);
}