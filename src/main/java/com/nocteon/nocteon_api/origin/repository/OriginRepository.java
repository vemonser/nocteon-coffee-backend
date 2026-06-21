package com.nocteon.nocteon_api.origin.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.origin.entity.Origin;

import io.lettuce.core.dynamic.annotation.Param;

public interface OriginRepository extends JpaRepository<Origin, Long> {
        @Query("""
                        SELECT c FROM Origin c
                        LEFT JOIN FETCH c.translations
                        WHERE c.slug = :slug
                        """)
        Optional<Origin> findBySlugWithTranslations(@Param("slug") String slug);

        @Query("""
                        SELECT c FROM Origin c
                        LEFT JOIN FETCH c.translations t
                        WHERE c.slug = :slug
                        AND t.language = :language
                        """)
        Optional<Origin> findBySlug(@Param("slug") String slug);

        Optional<Origin> findBySlugAndLanguage(
                        @Param("slug") String slug,
                        @Param("language") String language);

        @Query("""
                        SELECT DISTINCT c FROM Origin c
                        LEFT JOIN FETCH c.translations t
                        WHERE t.language = :language
                        ORDER BY c.id ASC
                        """)
        List<Origin> findAllWithLanguage(@Param("language") String language);

        boolean existsBySlug(String slug);

}
