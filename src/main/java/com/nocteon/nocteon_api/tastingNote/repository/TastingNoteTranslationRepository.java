package com.nocteon.nocteon_api.tastingNote.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.tastingNote.entity.TastingNoteTranslation;

public interface TastingNoteTranslationRepository extends JpaRepository<TastingNoteTranslation, Long> {
    Optional<TastingNoteTranslation> findByTastingNoteIdAndLanguage(Long id, String language);

    List<TastingNoteTranslation> findByTastingNoteId(Long id);
}
