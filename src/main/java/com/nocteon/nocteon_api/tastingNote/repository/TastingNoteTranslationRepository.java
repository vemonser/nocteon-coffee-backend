package com.nocteon.nocteon_api.tastingNote.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.tastingNote.entity.TastingNoteTranslation;

public interface TastingNoteTranslationRepository extends JpaRepository<TastingNoteTranslation, Long> {
    Optional<TastingNoteTranslation> findByTastingNoteIdAndLanguage(Long id, String language);

    List<TastingNoteTranslation> findByTastingNoteId(Long id);

    @Query(value = """
            SELECT (COUNT(*) > 0)
            FROM tasting_note_translations t
            JOIN tasting_notes p ON p.id = t.tasting_note_id
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
