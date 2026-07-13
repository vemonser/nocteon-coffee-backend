package com.nocteon.nocteon_api.origin.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.origin.entity.Origin;

import io.lettuce.core.dynamic.annotation.Param;

public interface OriginRepository extends JpaRepository<Origin, Long> {

    @Query("""
            SELECT DISTINCT o FROM Origin o
            LEFT JOIN o.translations t
            WHERE (:search = ''
                   OR (t.language = :language
                       AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))))
            """)
    Page<Origin> findAllPublic(
            @Param("search") String search,
            @Param("language") String language,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT o FROM Origin o
            LEFT JOIN o.translations t
            WHERE (:search = ''
                   OR (t.language = :language
                       AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))))
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
}