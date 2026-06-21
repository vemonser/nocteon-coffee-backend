package com.nocteon.nocteon_api.processingMethod.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethodTranslation;

public interface ProcessingMethodTranslationRepository extends JpaRepository<ProcessingMethodTranslation, Long> {
    Optional<ProcessingMethodTranslation> findByProcessingMethodIdAndLanguage(Long id, String language);

    List<ProcessingMethodTranslation> findByProcessingMethodId(Long id);
}
