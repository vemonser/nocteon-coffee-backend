package com.nocteon.nocteon_api.tastingNote.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;


public interface TastingNoteRepository extends JpaRepository<TastingNote, Long> {

    @Query("""
            SELECT DISTINCT t FROM TastingNote t
            LEFT JOIN t.translations tr
            WHERE (COALESCE(:search, '') = ''
                   OR (tr.language = :language
                       AND LOWER(tr.name) LIKE LOWER(CONCAT('%', :search, '%'))))
            """)
    Page<TastingNote> findAllPublic(
            @Param("search") String search,
            @Param("language") String language,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT t FROM TastingNote t
            LEFT JOIN t.translations tr
            WHERE (COALESCE(:search, '') = ''
                   OR LOWER(tr.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(t.slug) LIKE LOWER(CONCAT('%', :search, '%'))
                )
            """)
    Page<TastingNote> findAllDashboard(
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT t FROM TastingNote t
            LEFT JOIN FETCH t.translations
            WHERE t.slug = :slug
            """)
    Optional<TastingNote> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("""
            SELECT t FROM TastingNote t
            LEFT JOIN FETCH t.translations tr
            WHERE t.slug = :slug AND tr.language = :language
            """)
    Optional<TastingNote> findBySlugAndLanguage(
            @Param("slug") String slug,
            @Param("language") String language);

    List<TastingNote> findBySlugIn(List<String> slugs);

    boolean existsBySlug(String slug);

        @Query(value = """
                SELECT e.id, e.slug, e.created_at, et.language, et.name
                FROM tasting_notes e
                JOIN tasting_note_translations et ON e.id = et.tasting_note_id
                WHERE e.deleted_at IS NOT NULL
                GROUP BY e.id, e.slug, e.created_at, et.language, et.name
                """, nativeQuery = true)
        List<Object[]> findAllDeletedNative();

        @Query(value = """
                SELECT e.id, e.slug, e.created_at, et.language, et.name
                FROM tasting_notes e
                JOIN tasting_note_translations et ON e.id = et.tasting_note_id
                WHERE e.deleted_at IS NOT NULL
                AND (LOWER(et.name) LIKE LOWER(CONCAT('%', :search, '%'))
                     OR LOWER(e.slug) LIKE LOWER(CONCAT('%', :search, '%')))
                GROUP BY e.id, e.slug, e.created_at, et.language, et.name
                """, nativeQuery = true)
        List<Object[]> findDeletedBySearchNative(@Param("search") String search);

        @Query(value = "SELECT e.* FROM tasting_notes e WHERE e.id = :id AND e.deleted_at IS NOT NULL", nativeQuery = true)
        Optional<TastingNote> findDeletedById(@Param("id") Long id);

}
