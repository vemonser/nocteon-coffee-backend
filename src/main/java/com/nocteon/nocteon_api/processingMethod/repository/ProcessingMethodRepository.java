package com.nocteon.nocteon_api.processingMethod.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;


public interface ProcessingMethodRepository extends JpaRepository<ProcessingMethod, Long> {

    @Query("""
            SELECT DISTINCT p FROM ProcessingMethod p
            LEFT JOIN p.translations t
            WHERE (COALESCE(:search, '') = ''
                   OR (t.language = :language
                       AND LOWER(t.name) LIKE :search))
            """)
    Page<ProcessingMethod> findAllPublic(
            @Param("search") String search,
            @Param("language") String language,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT p FROM ProcessingMethod p
            LEFT JOIN p.translations t
            WHERE (
            COALESCE(:search, '') = ''
            OR LOWER(t.name) LIKE :search
            OR LOWER(p.slug) LIKE :search
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

    @Query(value = """
        SELECT e.id, e.slug, e.created_at,
               et.language, et.name, et.description
        FROM processing_methods e
        JOIN processing_method_translations et ON e.id = et.processing_method_id
        WHERE e.deleted_at IS NOT NULL
        GROUP BY e.id, e.slug, e.created_at, et.language, et.name, et.description
        """, nativeQuery = true)
    List<Object[]> findAllDeletedNative();

    @Query(value = """
        SELECT e.id, e.slug, e.created_at,
               et.language, et.name, et.description
        FROM processing_methods e
        JOIN processing_method_translations et ON e.id = et.processing_method_id
        WHERE e.deleted_at IS NOT NULL
        AND (LOWER(et.name) LIKE :search OR LOWER(e.slug) LIKE :search)
        GROUP BY e.id, e.slug, e.created_at, et.language, et.name, et.description
        """, nativeQuery = true)
    List<Object[]> findDeletedBySearchNative(@Param("search") String search);

    @Query(value = "SELECT e.* FROM processing_methods e WHERE e.id = :id AND e.deleted_at IS NOT NULL", nativeQuery = true)
    Optional<ProcessingMethod> findDeletedById(@Param("id") Long id);
}