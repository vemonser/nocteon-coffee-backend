package com.nocteon.nocteon_api.journal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.journal.entity.JournalPostTranslation;

public interface JournalPostTranslationRepository
        extends JpaRepository<JournalPostTranslation, Long> {
    Optional<JournalPostTranslation> findByJournalPostIdAndLanguage(Long postId, String language);
    List<JournalPostTranslation> findByJournalPostId(Long postId);
}
