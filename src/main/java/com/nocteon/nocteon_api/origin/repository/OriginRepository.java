package com.nocteon.nocteon_api.origin.repository;

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
                        LEFT JOIN FETCH o.translations t
                        WHERE t.language = :language
                        AND (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
                        """)
        Page<Origin> findAllPublic(
                        @Param("language") String language,
                        @Param("search") String search,
                        Pageable pageable);

        @Query("""
                        SELECT DISTINCT o FROM Origin o
                        LEFT JOIN FETCH o.translations t
                        WHERE (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
                        """)
        Page<Origin> findAllDashboard(
                        @Param("search") String search,
                        Pageable pageable);

        @Query("SELECT o FROM Origin o LEFT JOIN FETCH o.translations WHERE o.slug = :slug")
        Optional<Origin> findBySlugWithTranslations(@Param("slug") String slug);

        @Query("SELECT o FROM Origin o LEFT JOIN FETCH o.translations t WHERE o.slug = :slug AND t.language = :language")
        Optional<Origin> findBySlugAndLanguage(@Param("slug") String slug, @Param("language") String language);

        Optional<Origin> findBySlug(String slug);

        boolean existsBySlug(String slug);
}