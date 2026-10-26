package com.nocteon.nocteon_api.origin.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.origin.entity.OriginTranslation;

public interface OriginTranslationRepository extends JpaRepository<OriginTranslation, Long> {
    Optional<OriginTranslation> findByOriginIdAndLanguage(Long categoryId, String language);

    List<OriginTranslation> findByOriginId(Long id);
    void deleteByOriginId(Long originId);

    @Query(value = """
            SELECT (COUNT(*) > 0)
            FROM origin_translations t
            JOIN origins p ON p.id = t.origin_id
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
