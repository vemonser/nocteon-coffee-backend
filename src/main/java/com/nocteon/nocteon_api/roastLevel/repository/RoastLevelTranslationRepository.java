package com.nocteon.nocteon_api.roastLevel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.roastLevel.entity.RoastLevelTranslation;

public interface RoastLevelTranslationRepository extends JpaRepository<RoastLevelTranslation, Long> {
    Optional<RoastLevelTranslation> findByRoastLevelIdAndLanguage(Long id, String language);

    List<RoastLevelTranslation> findByRoastLevelId(Long id);

}
