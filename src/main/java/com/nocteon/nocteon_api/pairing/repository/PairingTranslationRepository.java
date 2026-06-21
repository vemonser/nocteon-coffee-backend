package com.nocteon.nocteon_api.pairing.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.pairing.entity.PairingTranslation;

public interface PairingTranslationRepository extends JpaRepository<PairingTranslation, Long> {
    Optional<PairingTranslation> findByPairingIdAndLanguage(Long id, String language);
    List<PairingTranslation> findByPairingId(Long id);
}