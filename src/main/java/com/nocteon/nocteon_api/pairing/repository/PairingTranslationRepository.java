package com.nocteon.nocteon_api.pairing.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.pairing.entity.PairingTranslation;

public interface PairingTranslationRepository extends JpaRepository<PairingTranslation, Long> {
    Optional<PairingTranslation> findByPairingIdAndLanguage(Long id, String language);
    List<PairingTranslation> findByPairingId(Long id);

    @Query(value = """
            SELECT (COUNT(*) > 0)
            FROM pairing_translations t
            JOIN pairings p ON p.id = t.pairing_id
            WHERE t.language = :language
              AND LOWER(REGEXP_REPLACE(t.name, '^[ \\t\\n\\r\\f\\v]+|[ \\t\\n\\r\\f\\v]+$', '', 'g')) =
                  LOWER(REGEXP_REPLACE(:name, '^[ \\t\\n\\r\\f\\v]+|[ \\t\\n\\r\\f\\v]+$', '', 'g'))
              AND p.deleted_at IS NULL
              AND (:excludeId IS NULL OR p.id <> :excludeId)
            """, nativeQuery = true)
    boolean existsLiveTranslationName(@Param("language") String language,
                                     @Param("name") String name,
                                     @Param("excludeId") Long excludeId);
}