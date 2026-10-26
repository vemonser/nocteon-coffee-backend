package com.nocteon.nocteon_api.pairing.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.pairing.entity.Pairing;


public interface PairingRepository extends JpaRepository<Pairing, Long> {

    @Query("""
            SELECT DISTINCT p FROM Pairing p
            LEFT JOIN p.translations t
            WHERE (COALESCE(:search, '') = ''
                   OR (t.language = :language
                       AND LOWER(t.name) LIKE :search))
            """)
    Page<Pairing> findAllPublic(
            @Param("search") String search,
            @Param("language") String language,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT p FROM Pairing p
            LEFT JOIN p.translations t
            WHERE (COALESCE(:search, '') = ''
                   OR  LOWER(t.name) LIKE :search
                   OR  LOWER(p.slug) LIKE :search
                )
            """)
    Page<Pairing> findAllDashboard(
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT p FROM Pairing p
            LEFT JOIN FETCH p.translations
            WHERE p.slug = :slug
            """)
    Optional<Pairing> findBySlugWithTranslations(@Param("slug") String slug);
    @Query("""
        select p
        from Product product
        join product.pairings p
        left join fetch p.translations t
        where product.id = :productId
        and t.language = :language
    """)
    // Annotated explicitly even though the call order happens to match: without @Param this binds
    // positionally, so reordering the parameters would silently swap productId and language and
    // return pairings for the wrong locale rather than fail.
    List<Pairing> findByProductId(@Param("productId") Long productId, @Param("language") String language);

    @Query("""
            SELECT p FROM Pairing p
            LEFT JOIN FETCH p.translations t
            WHERE p.slug = :slug AND t.language = :language
            """)
    Optional<Pairing> findBySlugAndLanguage(
            @Param("slug") String slug,
            @Param("language") String language);

    Optional<Pairing> findBySlug(String slug);

    List<Pairing> findBySlugIn(List<String> slugs);

    boolean existsBySlug(String slug);

    /**
     * Counts soft-deleted rows too, because the pairings.slug column carries a plain UNIQUE
     * constraint (not a partial index). Without this, reusing a name after a soft delete would
     * generate the slug still held by the trashed row and fail with a constraint violation.
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM pairings WHERE slug = :slug", nativeQuery = true)
    boolean existsBySlugIncludingDeleted(@Param("slug") String slug);

        @Query(value = """
                SELECT e.id, e.slug, e.image_url, e.created_at, et.language, et.name, et.description
                FROM pairings e
                JOIN pairing_translations et ON e.id = et.pairing_id
                WHERE e.deleted_at IS NOT NULL
                GROUP BY e.id, e.slug, e.image_url, e.created_at, et.language, et.name, et.description
                """, nativeQuery = true)
        List<Object[]> findAllDeletedNative();

        @Query(value = """
                SELECT e.id, e.slug, e.image_url, e.created_at, et.language, et.name, et.description
                FROM pairings e
                JOIN pairing_translations et ON e.id = et.pairing_id
                WHERE e.deleted_at IS NOT NULL
                AND (LOWER(et.name) LIKE :search
                     OR LOWER(e.slug) LIKE :search)
                GROUP BY e.id, e.slug, e.image_url, e.created_at, et.language, et.name, et.description
                """, nativeQuery = true)
        List<Object[]> findDeletedBySearchNative(@Param("search") String search);

        @Query(value = "SELECT e.* FROM pairings e WHERE e.id = :id AND e.deleted_at IS NOT NULL", nativeQuery = true)
        Optional<Pairing> findDeletedById(@Param("id") Long id);

}
