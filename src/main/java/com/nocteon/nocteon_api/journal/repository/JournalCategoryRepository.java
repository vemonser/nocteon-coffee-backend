package com.nocteon.nocteon_api.journal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.journal.entity.JournalCategory;

import io.lettuce.core.dynamic.annotation.Param;

public interface JournalCategoryRepository extends JpaRepository<JournalCategory, Long> {

    @Query("""
            SELECT DISTINCT c FROM JournalCategory c
            LEFT JOIN FETCH c.translations t
            WHERE t.language = :language
            """)
    List<JournalCategory> findAllWithLanguage(@Param("language") String language);

    @Query("SELECT c FROM JournalCategory c LEFT JOIN FETCH c.translations WHERE c.slug = :slug")
    Optional<JournalCategory> findBySlugWithTranslations(@Param("slug") String slug);

    Optional<JournalCategory> findBySlug(String slug);
    boolean existsBySlug(String slug);
}