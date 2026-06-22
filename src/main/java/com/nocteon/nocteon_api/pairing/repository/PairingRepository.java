package com.nocteon.nocteon_api.pairing.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.pairing.entity.Pairing;

import io.lettuce.core.dynamic.annotation.Param;

public interface PairingRepository extends JpaRepository<Pairing, Long> {

    @Query("""
            SELECT DISTINCT p FROM Pairing p
            LEFT JOIN FETCH p.translations t
            WHERE t.language = :language
            AND (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Pairing> findAllPublic(
            @Param("language") String language,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT p FROM Pairing p
            LEFT JOIN FETCH p.translations t
            WHERE (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Pairing> findAllDashboard(
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT p FROM Pairing p LEFT JOIN FETCH p.translations WHERE p.slug = :slug")
    Optional<Pairing> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("SELECT p FROM Pairing p LEFT JOIN FETCH p.translations t WHERE p.slug = :slug AND t.language = :language")
    Optional<Pairing> findBySlugAndLanguage(@Param("slug") String slug, @Param("language") String language);

    List<Pairing> findBySlugIn(List<String> slugs);

    boolean existsBySlug(String slug);
}