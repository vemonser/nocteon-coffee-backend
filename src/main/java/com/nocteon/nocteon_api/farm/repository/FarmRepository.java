package com.nocteon.nocteon_api.farm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.farm.entity.Farm;

import io.lettuce.core.dynamic.annotation.Param;

public interface FarmRepository extends JpaRepository<Farm, Long> {

    @Query("""
            SELECT DISTINCT c FROM Farm c
            LEFT JOIN FETCH c.translations t
            WHERE c.origin.slug = :originSlug
            AND t.language = :language
            ORDER BY c.id ASC
            """)
    List<Farm> findAllByOriginSlugWithLanguage(
            @Param("originSlug") String originSlug,
            @Param("language") String language);

    @Query("""
            SELECT c FROM Farm c
            LEFT JOIN FETCH c.translations
            WHERE c.slug = :slug
            """)
    Optional<Farm> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("""
            SELECT c FROM Farm c
            LEFT JOIN FETCH c.translations t
            WHERE c.slug = :slug
            AND t.language = :language
            """)
    Optional<Farm> findBySlugAndLanguage(
            @Param("slug") String slug,
            @Param("language") String language);

    @Query("""
            SELECT DISTINCT c FROM Farm c
            LEFT JOIN FETCH c.translations t
            WHERE t.language = :language
            ORDER BY c.id ASC
            """)
    List<Farm> findAllWithLanguage(@Param("language") String language);

    boolean existsBySlug(String slug);

}
