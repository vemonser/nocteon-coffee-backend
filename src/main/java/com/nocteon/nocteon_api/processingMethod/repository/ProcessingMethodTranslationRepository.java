package com.nocteon.nocteon_api.processingMethod.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethodTranslation;

public interface ProcessingMethodTranslationRepository extends JpaRepository<ProcessingMethodTranslation, Long> {
    Optional<ProcessingMethodTranslation> findByProcessingMethodIdAndLanguage(Long id, String language);

    List<ProcessingMethodTranslation> findByProcessingMethodId(Long id);

    @Query(value = """
            SELECT (COUNT(*) > 0)
            FROM processing_method_translations t
            JOIN processing_methods p ON p.id = t.processing_method_id
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
