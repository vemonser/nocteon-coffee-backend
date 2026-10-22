package com.nocteon.nocteon_api.tastingNote.service;

import java.time.Instant;

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
import com.nocteon.nocteon_api.common.service.SoftDeleteRowFolder;
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
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

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
                                SearchPatterns.like(filter.getSearch()),
                                language, filter.toPageable());
                return PageResponse.of(page.map(t -> buildResponse(t, language)));
        }

        public PageResponse<TastingNoteResponseDashboard> getAllDashboard(LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());

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
        public TastingNoteResponseDashboard create(TastingNoteRequest request) {
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
                var reloaded = tastingNoteRepository.findBySlugWithTranslations(slug).orElse(tastingNote);
                return buildResponse(reloaded);
        }

        @Transactional
        public TastingNoteResponseDashboard update(String slug, TastingNoteRequest request) {
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
                var reloaded = tastingNoteRepository.findBySlugWithTranslations(tastingNote.getSlug()).orElse(tastingNote);

                return buildResponse(reloaded);
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

        /**
         * Trashed TastingNotes for the dashboard's deleted-items table.
         *
         * <p>Paged in memory rather than in SQL. {@code @SQLRestriction} hides trashed rows from
         * every JPQL and derived query, so the rows have to come from a native query that also
         * bypasses {@code LIMIT}/{@code OFFSET} pagination. That is cheap while the trash is small
         * and degrades linearly as it grows — acceptable for an admin recovery screen, and the same
         * trade the Category listing already makes.
         *
         * <p>Folds the per-language rows onto one response per entity. The native query returns one
         * row per translation, so mapping rows directly would list a deleted item once per
         * translation and report a row count as its total — which also splits one item's
         * translations across two pages. See {@link SoftDeleteRowFolder}.
         */
        public PageResponse<TastingNoteResponseDashboard> getAllDeleted(LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());
                int pageNum = filter.getPage();
                int size = filter.getSize();
                String sort = filter.getSort();
                String direction = filter.getDirection();

                List<Object[]> rawResults = tastingNoteRepository.findDeletedBySearchNative(search);

                List<TastingNoteResponseDashboard> allResults = SoftDeleteRowFolder.fold(
                                rawResults,
                                row -> ((Number) row[0]).longValue(),
                                this::mapToTastingNoteResponseDashboard,
                                (response, row) -> response.getTranslations()
                                                .add(buildTranslation(row)));

                if (sort != null) {
                        allResults.sort((a, b) -> {
                                int result = switch (sort) {
                                        case "slug" -> a.getSlug().compareToIgnoreCase(b.getSlug());
                                        case "createdAt" -> a.getCreatedAt().compareTo(b.getCreatedAt());
                                        default -> 0;
                                };
                                return direction.equalsIgnoreCase("desc") ? -result : result;
                        });
                }

                int fromIndex = pageNum * size;
                if (fromIndex > allResults.size()) {
                        fromIndex = allResults.size();
                }
                int toIndex = Math.min(fromIndex + size, allResults.size());
                List<TastingNoteResponseDashboard> pagedResults = allResults.subList(fromIndex, toIndex);

                Page<TastingNoteResponseDashboard> page = new org.springframework.data.domain.PageImpl<>(
                                pagedResults,
                                org.springframework.data.domain.PageRequest.of(pageNum, size),
                                allResults.size());
                return PageResponse.of(page);
        }

        private TastingNoteResponseDashboard mapToTastingNoteResponseDashboard(Object[] row) {
                return TastingNoteResponseDashboard.builder()
                                .id(((Number) row[0]).longValue())
                                .slug((String) row[1])
                                .createdAt((Instant) row[2])
                                .translations(new ArrayList<>())
                                .build();
        }

        private TranslationResponseDashboard buildTranslation(Object[] row) {
                return TranslationResponseDashboard.builder()
                                .language((String) row[3])
                                .name((String) row[4])


                                .build();
        }

        /**
         * Un-delete a TastingNote.
         *
         * <p>Reads through {@code findDeletedById}, so an id that is live or unknown raises
         * {@link TastingNoteNotFoundException} rather than reporting a restore that changed nothing.
         */
        @Transactional
        public void restore(Long id) {
                TastingNote tastingNote = tastingNoteRepository.findDeletedById(id)
                                .orElseThrow(TastingNoteNotFoundException::new);
                tastingNote.setDeletedAt(null);
                tastingNoteRepository.save(tastingNote);
                log.info("TastingNote restored: {}", tastingNote.getSlug());
        }

        /**
         * Permanently deletes a trashed TastingNote: removes the row itself (it carries no media, so there is nothing to clean on Cloudinary).
         *
         * <p>Trash-only: reads through {@code findDeletedById}, so a live or unknown id raises
         * {@link TastingNoteNotFoundException} instead of deleting anything. Soft-delete first — this never touches live
         * rows.
         */
        @Transactional
        public void permanentDelete(Long id) {
                TastingNote tastingNote = tastingNoteRepository.findDeletedById(id)
                                .orElseThrow(TastingNoteNotFoundException::new);
                tastingNoteRepository.delete(tastingNote);
                log.info("TastingNote permanently deleted: {}", tastingNote.getSlug());
        }
}
