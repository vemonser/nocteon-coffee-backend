package com.nocteon.nocteon_api.brewingMethod.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethodTranslation;

public interface BrewingMethodTranslationRepository extends JpaRepository<BrewingMethodTranslation, Long> {
    Optional<BrewingMethodTranslation> findByBrewingMethodIdAndLanguage(Long id, String language);

    List<BrewingMethodTranslation> findByBrewingMethodId(Long id);

    @Query(value = """
            SELECT (COUNT(*) > 0)
            FROM brewing_method_translations t
            JOIN brewing_methods p ON p.id = t.brewing_method_id
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
