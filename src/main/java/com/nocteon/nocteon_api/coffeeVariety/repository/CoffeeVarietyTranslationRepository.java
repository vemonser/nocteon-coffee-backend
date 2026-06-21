package com.nocteon.nocteon_api.coffeeVariety.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVarietyTranslation;

public interface CoffeeVarietyTranslationRepository extends JpaRepository<CoffeeVarietyTranslation, Long> {
    Optional<CoffeeVarietyTranslation> findByCoffeeVarietyIdAndLanguage(Long id, String language);

    List<CoffeeVarietyTranslation> findByCoffeeVarietyId(Long id);
}
