package com.nocteon.nocteon_api.roastProfile.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.roastProfile.entity.RoastProfileTranslation;

public interface RoastProfileTranslationRepository extends JpaRepository<RoastProfileTranslation, Long> {
    Optional<RoastProfileTranslation> findByRoastProfileIdAndLanguage(Long id, String language);

    List<RoastProfileTranslation> findByRoastProfileId(Long id);

}
