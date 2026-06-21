package com.nocteon.nocteon_api.roastProfile.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.roastProfile.entity.RoastProfile;

import io.lettuce.core.dynamic.annotation.Param;

public interface RoastProfileRepository extends JpaRepository<RoastProfile, Long> {
    @Query("SELECT r FROM RoastProfile r LEFT JOIN FETCH r.translations WHERE r.slug = :slug")
    Optional<RoastProfile> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("SELECT r FROM RoastProfile r LEFT JOIN FETCH r.translations t WHERE r.slug = :slug AND t.language = :language")
    Optional<RoastProfile> findBySlugAndLanguage(@Param("slug") String slug, @Param("language") String language);

    @Query("SELECT DISTINCT r FROM RoastProfile r LEFT JOIN FETCH r.translations t WHERE t.language = :language ORDER BY r.id ASC")
    List<RoastProfile> findAllWithLanguage(@Param("language") String language);

    boolean existsBySlug(String slug);
}
