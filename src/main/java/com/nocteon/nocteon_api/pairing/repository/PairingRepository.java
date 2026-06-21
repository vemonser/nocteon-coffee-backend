package com.nocteon.nocteon_api.pairing.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.pairing.entity.Pairing;

import io.lettuce.core.dynamic.annotation.Param;

public interface PairingRepository extends JpaRepository<Pairing, Long> {

    @Query("SELECT p FROM Pairing p LEFT JOIN FETCH p.translations WHERE p.slug = :slug")
    Optional<Pairing> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("SELECT p FROM Pairing p LEFT JOIN FETCH p.translations t WHERE p.slug = :slug AND t.language = :language")
    Optional<Pairing> findBySlugAndLanguage(@Param("slug") String slug, @Param("language") String language);

    @Query("SELECT DISTINCT p FROM Pairing p LEFT JOIN FETCH p.translations t WHERE t.language = :language ORDER BY p.id ASC")
    List<Pairing> findAllWithLanguage(@Param("language") String language);

    boolean existsBySlug(String slug);
}
