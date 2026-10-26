package com.nocteon.nocteon_api.coffeeVariety.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVarietyTranslation;

public interface CoffeeVarietyTranslationRepository extends JpaRepository<CoffeeVarietyTranslation, Long> {
    Optional<CoffeeVarietyTranslation> findByCoffeeVarietyIdAndLanguage(Long id, String language);

    List<CoffeeVarietyTranslation> findByCoffeeVarietyId(Long id);

    @Query(value = """
            SELECT (COUNT(*) > 0)
            FROM coffee_variety_translations t
            JOIN coffee_varieties p ON p.id = t.coffee_variety_id
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
