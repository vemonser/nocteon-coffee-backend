package com.nocteon.nocteon_api.farm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.farm.entity.FarmTranslation;

public interface FarmTranslationRepository extends JpaRepository<FarmTranslation, Long> {

    Optional<FarmTranslation> findByFarmIdAndLanguage(Long farmId, String language);

    List<FarmTranslation> findByFarmId(Long id);

    void deleteByFarmId(Long originId);
}
