package com.nocteon.nocteon_api.roastLevel.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;


public interface RoastLevelRepository extends JpaRepository<RoastLevel, Long> {

    @Query("""
            SELECT DISTINCT r FROM RoastLevel r
            LEFT JOIN r.translations t
            WHERE (COALESCE(:search, '') = ''
                   OR (t.language = :language
                       AND LOWER(t.name) LIKE :search))
            """)
    Page<RoastLevel> findAllPublic(
            @Param("search") String search,
            @Param("language") String language,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT r FROM RoastLevel r
            LEFT JOIN  r.translations t
                        WHERE (COALESCE(:search, '') = ''
            OR LOWER(t.name) LIKE :search
            OR LOWER(r.slug) LIKE :search
            )
            """)
    Page<RoastLevel> findAllDashboard(
                        @Param("search") String search,
                        Pageable pageable);

    @Query("SELECT r FROM RoastLevel r LEFT JOIN FETCH r.translations WHERE r.slug = :slug")
    Optional<RoastLevel> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("SELECT r FROM RoastLevel r LEFT JOIN FETCH r.translations t WHERE r.slug = :slug AND t.language = :language")
    Optional<RoastLevel> findBySlugAndLanguage(@Param("slug") String slug, @Param("language") String language);
    
    Optional<RoastLevel> findBySlug(@Param("slug") String slug);

    boolean existsBySlug(String slug);

    /**
     * Counts soft-deleted rows too, because the roast_levels.slug column carries a plain UNIQUE
     * constraint (not a partial index). Without this, reusing a name after a soft delete would
     * generate the slug still held by the trashed row and fail with a constraint violation.
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM roast_levels WHERE slug = :slug", nativeQuery = true)
    boolean existsBySlugIncludingDeleted(@Param("slug") String slug);

    @Query("SELECT r FROM RoastLevel r LEFT JOIN FETCH r.translations")
    List<RoastLevel> findAllWithTranslations();

    @Query("SELECT r FROM RoastLevel r LEFT JOIN FETCH r.translations WHERE r.slug IN :slugs")
    List<RoastLevel> findBySlugInWithTranslations(@Param("slugs") List<String> slugs);

    @Query(value = """
        SELECT e.id, e.slug, e.color, e.created_at, e.updated_at,
               et.language, et.name, et.description
        FROM roast_levels e
        JOIN roast_level_translations et ON e.id = et.roast_level_id
        WHERE e.deleted_at IS NOT NULL
        GROUP BY e.id, e.slug, e.color, e.created_at, e.updated_at, et.language, et.name, et.description
        """, nativeQuery = true)
    List<Object[]> findAllDeletedNative();

    @Query(value = """
        SELECT e.id, e.slug, e.color, e.created_at, e.updated_at,
               et.language, et.name, et.description
        FROM roast_levels e
        JOIN roast_level_translations et ON e.id = et.roast_level_id
        WHERE e.deleted_at IS NOT NULL
        AND (LOWER(et.name) LIKE :search OR LOWER(e.slug) LIKE :search)
        GROUP BY e.id, e.slug, e.color, e.created_at, e.updated_at, et.language, et.name, et.description
        """, nativeQuery = true)
    List<Object[]> findDeletedBySearchNative(@Param("search") String search);

    @Query(value = "SELECT e.* FROM roast_levels e WHERE e.id = :id AND e.deleted_at IS NOT NULL", nativeQuery = true)
    Optional<RoastLevel> findDeletedById(@Param("id") Long id);
}