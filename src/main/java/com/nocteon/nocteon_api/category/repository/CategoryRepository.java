package com.nocteon.nocteon_api.category.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.category.entity.Category;

import io.lettuce.core.dynamic.annotation.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    @Query("""
            SELECT c FROM Category c
            LEFT JOIN FETCH c.translations
            WHERE c.slug = :slug
            """)
    Optional<Category> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("""
            SELECT c FROM Category c
            LEFT JOIN FETCH c.translations t
            WHERE c.slug = :slug
            AND t.language = :language
            """)
    Optional<Category> findBySlugAndLanguage(
            @Param("slug") String slug,
            @Param("language") String language);

    @Query("""
            SELECT DISTINCT c FROM Category c
            LEFT JOIN FETCH c.translations t
            WHERE t.language = :language
            ORDER BY c.id ASC
            """)
    List<Category> findAllWithLanguage(@Param("language") String language);

    boolean existsBySlug(String slug);

}
