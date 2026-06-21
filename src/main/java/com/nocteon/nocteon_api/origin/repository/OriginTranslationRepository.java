package com.nocteon.nocteon_api.origin.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.origin.entity.OriginTranslation;

public interface OriginTranslationRepository extends JpaRepository<OriginTranslation, Long> {
    Optional<OriginTranslation> findByOriginIdAndLanguage(Long categoryId, String language);

    List<OriginTranslation> findByOriginId(Long id);
    void deleteByOriginId(Long originId);
}
