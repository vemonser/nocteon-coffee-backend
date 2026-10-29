package com.nocteon.nocteon_api.origin.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.origin.entity.Origin;


public interface OriginRepository extends JpaRepository<Origin, Long> {

    @Query("""
            SELECT DISTINCT o FROM Origin o
            LEFT JOIN o.translations t
            WHERE (COALESCE(:search, '') = ''
                   OR (t.language = :language
                       AND LOWER(t.name) LIKE :search))
            """)
    Page<Origin> findAllPublic(
            @Param("search") String search,
            @Param("language") String language,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT o FROM Origin o
            LEFT JOIN o.translations t
            WHERE (COALESCE(:search, '') = ''
                   OR (t.language = :language
                       AND LOWER(t.name) LIKE :search))
            """)
    Page<Origin> findAllDashboard(
            @Param("search") String search,
            @Param("language") String language,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT o FROM Origin o
            LEFT JOIN FETCH o.translations
            WHERE o.slug = :slug
            """)
    Optional<Origin> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("SELECT o FROM Origin o LEFT JOIN FETCH o.translations")
    List<Origin> findAllForOptions();

    @Query("""
            SELECT o FROM Origin o
            LEFT JOIN FETCH o.translations t
            WHERE o.slug = :slug AND t.language = :language
            """)
    Optional<Origin> findBySlugAndLanguage(
            @Param("slug") String slug,
            @Param("language") String language);

    Optional<Origin> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * Counts soft-deleted rows too, because {@code origins.slug} carries a plain UNIQUE
     * constraint (not a partial index). Without this, reusing a name after a soft delete would
     * generate the slug still held by the trashed row and fail with a constraint violation.
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM origins WHERE slug = :slug", nativeQuery = true)
    boolean existsBySlugIncludingDeleted(@Param("slug") String slug);

    @Query("SELECT o FROM Origin o LEFT JOIN FETCH o.translations")
    List<Origin> findAllWithTranslations();

    @Query("SELECT o FROM Origin o LEFT JOIN FETCH o.translations WHERE o.slug IN :slugs")
    List<Origin> findBySlugInWithTranslations(@Param("slugs") List<String> slugs);

    @Query(value = """
        SELECT e.id, e.slug, e.code, e.image_url, e.created_at,
               et.language, et.name, et.description
        FROM origins e
        JOIN origin_translations et ON e.id = et.origin_id
        WHERE e.deleted_at IS NOT NULL
        GROUP BY e.id, e.slug, e.code, e.image_url, e.created_at, et.language, et.name, et.description
        """, nativeQuery = true)
    List<Object[]> findAllDeletedNative();

    @Query(value = """
        SELECT e.id, e.slug, e.code, e.image_url, e.created_at,
               et.language, et.name, et.description
        FROM origins e
        JOIN origin_translations et ON e.id = et.origin_id
        WHERE e.deleted_at IS NOT NULL
        AND (COALESCE(:search, '') = '' OR LOWER(et.name) LIKE :search OR LOWER(e.slug) LIKE :search)
        GROUP BY e.id, e.slug, e.code, e.image_url, e.created_at, et.language, et.name, et.description
        """, nativeQuery = true)
    List<Object[]> findDeletedBySearchNative(@Param("search") String search);

    @Query(value = "SELECT e.* FROM origins e WHERE e.id = :id AND e.deleted_at IS NOT NULL", nativeQuery = true)
    Optional<Origin> findDeletedById(@Param("id") Long id);
}