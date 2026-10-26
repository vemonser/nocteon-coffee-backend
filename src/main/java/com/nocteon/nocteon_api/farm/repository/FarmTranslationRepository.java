package com.nocteon.nocteon_api.farm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.farm.entity.FarmTranslation;

public interface FarmTranslationRepository extends JpaRepository<FarmTranslation, Long> {

    Optional<FarmTranslation> findByFarmIdAndLanguage(Long farmId, String language);

    List<FarmTranslation> findByFarmId(Long id);

    void deleteByFarmId(Long farmId);

    @Query(value = """
            SELECT (COUNT(*) > 0)
            FROM farm_translations t
            JOIN farms p ON p.id = t.farm_id
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
