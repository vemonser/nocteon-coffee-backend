package com.nocteon.nocteon_api.brewingMethod.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethodTranslation;

public interface BrewingMethodTranslationRepository extends JpaRepository<BrewingMethodTranslation, Long> {
    Optional<BrewingMethodTranslation> findByBrewingMethodIdAndLanguage(Long id, String language);

    List<BrewingMethodTranslation> findByBrewingMethodId(Long id);
}
