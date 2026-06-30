package com.nocteon.nocteon_api.tastingNote.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;

import io.lettuce.core.dynamic.annotation.Param;

public interface TastingNoteRepository extends JpaRepository<TastingNote, Long> {

    @Query("""
            SELECT DISTINCT t FROM TastingNote t
            LEFT JOIN t.translations tr
            WHERE (:search = ''
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
            WHERE (:search = ''
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
}