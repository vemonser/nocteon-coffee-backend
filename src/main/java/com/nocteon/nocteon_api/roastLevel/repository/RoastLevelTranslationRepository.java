package com.nocteon.nocteon_api.roastLevel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.roastLevel.entity.RoastLevelTranslation;

public interface RoastLevelTranslationRepository extends JpaRepository<RoastLevelTranslation, Long> {
    Optional<RoastLevelTranslation> findByRoastLevelIdAndLanguage(Long id, String language);

    List<RoastLevelTranslation> findByRoastLevelId(Long id);


    @Query(value = """
            SELECT (COUNT(*) > 0)
            FROM roast_level_translations t
            JOIN roast_levels p ON p.id = t.roast_level_id
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
