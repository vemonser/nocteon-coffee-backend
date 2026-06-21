package com.nocteon.nocteon_api.processingMethod.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;

import io.lettuce.core.dynamic.annotation.Param;

public interface ProcessingMethodRepository extends JpaRepository<ProcessingMethod, Long> {

    @Query("SELECT b FROM ProcessingMethod b LEFT JOIN FETCH b.translations WHERE b.slug = :slug")
    Optional<ProcessingMethod> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("SELECT b FROM ProcessingMethod b LEFT JOIN FETCH b.translations t WHERE b.slug = :slug AND t.language = :language")
    Optional<ProcessingMethod> findBySlugAndLanguage(@Param("slug") String slug, @Param("language") String language);

    @Query("SELECT DISTINCT b FROM ProcessingMethod b LEFT JOIN FETCH b.translations t WHERE t.language = :language ORDER BY b.id ASC")
    List<ProcessingMethod> findAllWithLanguage(@Param("language") String language);

    boolean existsBySlug(String slug);
}
