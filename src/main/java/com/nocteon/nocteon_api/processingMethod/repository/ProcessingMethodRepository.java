package com.nocteon.nocteon_api.processingMethod.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;

import io.lettuce.core.dynamic.annotation.Param;

public interface ProcessingMethodRepository extends JpaRepository<ProcessingMethod, Long> {

    @Query("""
            SELECT DISTINCT p FROM ProcessingMethod p
            LEFT JOIN p.translations t
            WHERE (:search = ''
                   OR (t.language = :language
                       AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))))
            """)
    Page<ProcessingMethod> findAllPublic(
            @Param("search") String search,
            @Param("language") String language,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT p FROM ProcessingMethod p
            LEFT JOIN p.translations t
            WHERE (
            :search = ''
            OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(p.slug) LIKE LOWER(CONCAT('%', :search, '%'))
        )
        """)
    Page<ProcessingMethod> findAllDashboard(
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT p FROM ProcessingMethod p
            LEFT JOIN FETCH p.translations
            WHERE p.slug = :slug
            """)
    Optional<ProcessingMethod> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("""
            SELECT p FROM ProcessingMethod p
            LEFT JOIN FETCH p.translations t
            WHERE p.slug = :slug AND t.language = :language
            """)
    Optional<ProcessingMethod> findBySlugAndLanguage(
            @Param("slug") String slug,
            @Param("language") String language);

    Optional<ProcessingMethod> findBySlug(String slug);

    boolean existsBySlug(String slug);
}