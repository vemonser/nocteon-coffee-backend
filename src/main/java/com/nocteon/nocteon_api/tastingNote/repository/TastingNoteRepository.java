package com.nocteon.nocteon_api.tastingNote.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;

import io.lettuce.core.dynamic.annotation.Param;

public interface TastingNoteRepository extends JpaRepository<TastingNote, Long> {

    @Query("SELECT b FROM TastingNote b LEFT JOIN FETCH b.translations WHERE b.slug = :slug")
    Optional<TastingNote> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("SELECT b FROM TastingNote b LEFT JOIN FETCH b.translations t WHERE b.slug = :slug AND t.language = :language")
    Optional<TastingNote> findBySlugAndLanguage(@Param("slug") String slug, @Param("language") String language);

    @Query("SELECT DISTINCT b FROM TastingNote b LEFT JOIN FETCH b.translations t WHERE t.language = :language ORDER BY b.id ASC")
    List<TastingNote> findAllWithLanguage(@Param("language") String language);

    boolean existsBySlug(String slug);
}
