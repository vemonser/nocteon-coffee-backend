package com.nocteon.nocteon_api.roastLevel.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;

import io.lettuce.core.dynamic.annotation.Param;

public interface RoastLevelRepository extends JpaRepository<RoastLevel, Long> {

    @Query("""
            SELECT DISTINCT r FROM RoastLevel r
            LEFT JOIN r.translations t
            WHERE (:search = ''
                   OR (t.language = :language
                       AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))))
            """)
    Page<RoastLevel> findAllPublic(
            @Param("search") String search,
            @Param("language") String language,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT r FROM RoastLevel r
            LEFT JOIN  r.translations t
                        WHERE (:search = ''
            OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(r.slug) LIKE LOWER(CONCAT('%', :search, '%'))
            )
            """)
    Page<RoastLevel> findAllDashboard(
                        @Param("search") String search,
                        Pageable pageable);

    @Query("SELECT r FROM RoastLevel r LEFT JOIN FETCH r.translations WHERE r.slug = :slug")
    Optional<RoastLevel> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("SELECT r FROM RoastLevel r LEFT JOIN FETCH r.translations t WHERE r.slug = :slug AND t.language = :language")
    Optional<RoastLevel> findBySlugAndLanguage(@Param("slug") String slug, @Param("language") String language);
    
    Optional<RoastLevel> findBySlug(@Param("slug") String slug);

    boolean existsBySlug(String slug);
}