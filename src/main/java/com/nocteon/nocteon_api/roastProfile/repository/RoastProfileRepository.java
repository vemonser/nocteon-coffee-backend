package com.nocteon.nocteon_api.roastProfile.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.roastProfile.entity.RoastProfile;

import io.lettuce.core.dynamic.annotation.Param;

public interface RoastProfileRepository extends JpaRepository<RoastProfile, Long> {

    @Query("""
            SELECT DISTINCT r FROM RoastProfile r
            LEFT JOIN FETCH r.translations t
            WHERE t.language = :language
            AND (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<RoastProfile> findAllPublic(
            @Param("language") String language,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT r FROM RoastProfile r
            LEFT JOIN FETCH r.translations t
            WHERE (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<RoastProfile> findAllDashboard(
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT r FROM RoastProfile r LEFT JOIN FETCH r.translations WHERE r.slug = :slug")
    Optional<RoastProfile> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("SELECT r FROM RoastProfile r LEFT JOIN FETCH r.translations t WHERE r.slug = :slug AND t.language = :language")
    Optional<RoastProfile> findBySlugAndLanguage(@Param("slug") String slug, @Param("language") String language);
    
    Optional<RoastProfile> findBySlug(@Param("slug") String slug);

    boolean existsBySlug(String slug);
}