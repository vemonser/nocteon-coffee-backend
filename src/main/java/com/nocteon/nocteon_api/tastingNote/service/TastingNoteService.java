package com.nocteon.nocteon_api.tastingNote.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.TastingNoteNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.tastingNote.dto.request.TastingNoteRequest;
import com.nocteon.nocteon_api.tastingNote.dto.request.TastingNoteTranslationRequest;
import com.nocteon.nocteon_api.tastingNote.dto.response.TastingNoteResponse;
import com.nocteon.nocteon_api.tastingNote.dto.response.TastingNoteResponseDashboard;
import com.nocteon.nocteon_api.tastingNote.dto.response.TranslationResponseDashboard;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNoteTranslation;
import com.nocteon.nocteon_api.tastingNote.repository.TastingNoteRepository;
import com.nocteon.nocteon_api.tastingNote.repository.TastingNoteTranslationRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TastingNoteService {

        private final TastingNoteRepository tastingNoteRepository;
        private final TastingNoteTranslationRepository translationRepository;
        private final LookupServiceHelper helper;

        public PageResponse<TastingNoteResponse> getAll(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Page<TastingNote> page = tastingNoteRepository.findAllPublic(
                                language, filter.getSearch(), filter.toPageable());
                return PageResponse.of(page.map(t -> buildResponse(t, language)));
        }

        public PageResponse<TastingNoteResponseDashboard> getAllDashboard(LookupFilterRequest filter) {
                String search = Objects.requireNonNullElse(
                                filter.getSearch(),
                                "");

                Page<TastingNote> page = tastingNoteRepository.findAllDashboard(
                                search, filter.toPageable());
                return PageResponse.of(
                                page.map(this::buildResponse));
        }

        public TastingNoteResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                TastingNote tastingNote = tastingNoteRepository.findBySlugAndLanguage(slug, language)
                                .orElseThrow(TastingNoteNotFoundException::new);
                return buildResponse(tastingNote, language);
        }

        public TastingNoteResponseDashboard getDashboardBySlug(String slug) {
                TastingNote tastingNote = tastingNoteRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(TastingNoteNotFoundException::new);
                return buildResponse(tastingNote);
        }

        @Transactional
        public TastingNoteResponse create(TastingNoteRequest request) {
                helper.validateTranslations(request.getTranslations());

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(TastingNoteTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                String slug = helper.generateUniqueSlug(englishName, tastingNoteRepository::existsBySlug);

                TastingNote tastingNote = TastingNote.builder()
                                .slug(slug)
                                .build();

                tastingNote = tastingNoteRepository.save(tastingNote);

                final TastingNote saved = tastingNote;
                List<TastingNoteTranslation> translations = new ArrayList<>();
                for (TastingNoteTranslationRequest t : request.getTranslations()) {
                        translations.add(TastingNoteTranslation.builder()
                                        .tastingNote(saved)
                                        .language(t.getLanguage())
                                        .name(t.getName())
                                        .build());
                }
                translationRepository.saveAll(translations);

                log.info("TastingNote created with slug: {}", slug);
                return buildResponse(tastingNote, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public TastingNoteResponse update(String slug, TastingNoteRequest request) {
                TastingNote tastingNote = tastingNoteRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(TastingNoteNotFoundException::new);

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
                        request.getTranslations().forEach(t -> translationRepository
                                        .findByTastingNoteIdAndLanguage(tastingNote.getId(), t.getLanguage())
                                        .ifPresentOrElse(
                                                        existing -> {
                                                                existing.setName(t.getName());
                                                                translationRepository.save(existing);
                                                        },
                                                        () -> translationRepository.save(
                                                                        TastingNoteTranslation.builder()
                                                                                        .tastingNote(tastingNote)
                                                                                        .language(t.getLanguage())
                                                                                        .name(t.getName())
                                                                                        .build())));
                }

                tastingNoteRepository.save(tastingNote);
                return buildResponse(tastingNote, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public void delete(String slug) {
                TastingNote tastingNote = tastingNoteRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(TastingNoteNotFoundException::new);
                tastingNote.softDelete();
                tastingNoteRepository.save(tastingNote);
                log.info("TastingNote soft deleted: {}", slug);
        }

        private TastingNoteResponse buildResponse(TastingNote tastingNote, String language) {
                List<TastingNoteTranslation> translations = translationRepository
                                .findByTastingNoteId(tastingNote.getId());

                TastingNoteTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                return TastingNoteResponse.builder()
                                .id(tastingNote.getId())
                                .slug(tastingNote.getSlug())
                                .createdAt(tastingNote.getCreatedAt())
                                .name(translation != null ? translation.getName() : null)
                                .build();
        }

        private TastingNoteResponseDashboard buildResponse(TastingNote tastingNote) {
                return TastingNoteResponseDashboard.builder()
                                .id(tastingNote.getId())
                                .slug(tastingNote.getSlug())
                                .createdAt(tastingNote.getCreatedAt())
                                .translations(
                                                tastingNote.getTranslations()
                                                                .stream()
                                                                .map(t -> TranslationResponseDashboard.builder()
                                                                                .language(t.getLanguage())
                                                                                .name(t.getName())
                                                                                .build())
                                                                .toList())
                                .build();
        }
}