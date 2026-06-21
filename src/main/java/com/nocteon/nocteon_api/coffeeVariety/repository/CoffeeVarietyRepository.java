package com.nocteon.nocteon_api.coffeeVariety.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVariety;

import io.lettuce.core.dynamic.annotation.Param;

public interface CoffeeVarietyRepository extends JpaRepository<CoffeeVariety, Long> {

    @Query("SELECT c FROM CoffeeVariety c LEFT JOIN FETCH c.translations WHERE c.slug = :slug")
    Optional<CoffeeVariety> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("SELECT c FROM CoffeeVariety c LEFT JOIN FETCH c.translations t WHERE c.slug = :slug AND t.language = :language")
    Optional<CoffeeVariety> findBySlugAndLanguage(@Param("slug") String slug, @Param("language") String language);

    @Query("SELECT DISTINCT c FROM CoffeeVariety c LEFT JOIN FETCH c.translations t WHERE t.language = :language ORDER BY c.id ASC")
    List<CoffeeVariety> findAllWithLanguage(@Param("language") String language);

    boolean existsBySlug(String slug);
}
