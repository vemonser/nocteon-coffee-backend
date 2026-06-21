package com.nocteon.nocteon_api.brewingMethod.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;

import io.lettuce.core.dynamic.annotation.Param;

public interface BrewingMethodRepository extends JpaRepository<BrewingMethod, Long> {

    @Query("SELECT b FROM BrewingMethod b LEFT JOIN FETCH b.translations WHERE b.slug = :slug")
    Optional<BrewingMethod> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("SELECT b FROM BrewingMethod b LEFT JOIN FETCH b.translations t WHERE b.slug = :slug AND t.language = :language")
    Optional<BrewingMethod> findBySlugAndLanguage(@Param("slug") String slug, @Param("language") String language);

    @Query("SELECT DISTINCT b FROM BrewingMethod b LEFT JOIN FETCH b.translations t WHERE t.language = :language ORDER BY b.id ASC")
    List<BrewingMethod> findAllWithLanguage(@Param("language") String language);

    boolean existsBySlug(String slug);
}
