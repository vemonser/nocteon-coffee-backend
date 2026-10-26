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
                       AND LOWER(t.name) LIKE :search))
            """)
    Page<BrewingMethod> findAllPublic(
            @Param("search") String search,
            @Param("language") String language,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT b FROM BrewingMethod b
            LEFT JOIN b.translations t
            WHERE (COALESCE(:search, '') = ''
                   OR  LOWER(t.name) LIKE :search
                   OR  LOWER(b.slug) LIKE :search
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

    /**
     * Counts soft-deleted rows too, because the brewing_methods.slug column carries a plain UNIQUE
     * constraint (not a partial index). Without this, reusing a name after a soft delete would
     * generate the slug still held by the trashed row and fail with a constraint violation.
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM brewing_methods WHERE slug = :slug", nativeQuery = true)
    boolean existsBySlugIncludingDeleted(@Param("slug") String slug);

        @Query(value = """
                SELECT e.id, e.slug, e.created_at, et.language, et.name, et.description
                FROM brewing_methods e
                JOIN brewing_method_translations et ON e.id = et.brewing_method_id
                WHERE e.deleted_at IS NOT NULL
                GROUP BY e.id, e.slug, e.created_at, et.language, et.name, et.description
                """, nativeQuery = true)
        List<Object[]> findAllDeletedNative();

        @Query(value = """
                SELECT e.id, e.slug, e.created_at, et.language, et.name, et.description
                FROM brewing_methods e
                JOIN brewing_method_translations et ON e.id = et.brewing_method_id
                WHERE e.deleted_at IS NOT NULL
                AND (LOWER(et.name) LIKE :search
                     OR LOWER(e.slug) LIKE :search)
                GROUP BY e.id, e.slug, e.created_at, et.language, et.name, et.description
                """, nativeQuery = true)
        List<Object[]> findDeletedBySearchNative(@Param("search") String search);

        @Query(value = "SELECT e.* FROM brewing_methods e WHERE e.id = :id AND e.deleted_at IS NOT NULL", nativeQuery = true)
        Optional<BrewingMethod> findDeletedById(@Param("id") Long id);

}
