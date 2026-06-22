package com.nocteon.nocteon_api.farm.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.farm.entity.Farm;

import io.lettuce.core.dynamic.annotation.Param;

public interface FarmRepository extends JpaRepository<Farm, Long> {

        @Query("""
                        SELECT DISTINCT f FROM Farm f
                        LEFT JOIN FETCH f.translations t
                        WHERE t.language = :language
                        AND (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
                        AND (:originSlug IS NULL OR f.origin.slug = :originSlug)
                        """)
        Page<Farm> findAllPublic(
                        @Param("language") String language,
                        @Param("search") String search,
                        @Param("originSlug") String originSlug,
                        Pageable pageable);

        @Query("""
                        SELECT DISTINCT f FROM Farm f
                        LEFT JOIN FETCH f.translations t
                        WHERE (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
                        AND (:originSlug IS NULL OR f.origin.slug = :originSlug)
                        """)
        Page<Farm> findAllDashboard(
                        @Param("search") String search,
                        @Param("originSlug") String originSlug,
                        Pageable pageable);

        @Query("SELECT f FROM Farm f LEFT JOIN FETCH f.translations WHERE f.slug = :slug")
        Optional<Farm> findBySlugWithTranslations(@Param("slug") String slug);

        @Query("SELECT f FROM Farm f LEFT JOIN FETCH f.translations t WHERE f.slug = :slug AND t.language = :language")
        Optional<Farm> findBySlugAndLanguage(@Param("slug") String slug, @Param("language") String language);

        Optional<Farm> findBySlug(@Param("slug") String slug);

        boolean existsBySlug(String slug);
}