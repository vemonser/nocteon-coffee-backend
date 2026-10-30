package com.nocteon.nocteon_api.tastingNote.service;

import java.time.Instant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.service.DomainAuditRecorder;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.TastingNoteNotFoundException;
import com.nocteon.nocteon_api.common.service.SoftDeleteRowFolder;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.common.service.TrashRules;
import com.nocteon.nocteon_api.common.service.TrashRules.Reference;
import com.nocteon.nocteon_api.common.service.TrashRules.Table;
import com.nocteon.nocteon_api.common.util.SlugUtils;
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

        /** The entity_type every row for this catalogue records. */
        private static final String ENTITY = "TastingNote";

        private final TastingNoteRepository tastingNoteRepository;
        private final TastingNoteTranslationRepository translationRepository;
        private final LookupServiceHelper helper;
        private final DomainAuditRecorder domainAudit;
        private final TrashRules trashRules;

        public PageResponse<TastingNoteResponse> getAll(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Page<TastingNote> page = tastingNoteRepository.findAllPublic(
                                SearchPatterns.like(filter.getSearch()),
                                language, filter.toPageable());
                return PageResponse.of(page.map(t -> buildResponse(t, language)));
        }

        @Transactional(readOnly = true)
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

        @Transactional(readOnly = true)
        public TastingNoteResponseDashboard getDashboardBySlug(String slug) {
                TastingNote tastingNote = tastingNoteRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(TastingNoteNotFoundException::new);
                return buildResponse(tastingNote);
        }

        @Transactional
        public TastingNoteResponseDashboard create(TastingNoteRequest request) {
                helper.validateTranslations(request.getTranslations());
                helper.assertNoDuplicateNames(
                                request.getTranslations(),
                                TastingNoteTranslationRequest::getLanguage,
                                TastingNoteTranslationRequest::getName,
                                "tasting-note",
                                null,
                                translationRepository::existsLiveTranslationName);

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(TastingNoteTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                // Serialize slug generation for names that slugify to the same base, and account
                // for soft-deleted rows: tasting-note slugs are a plain UNIQUE column, so a
                // trashed row still owns its slug even though the duplicate-name guard ignores
                // deleted rows.
                String baseSlug = SlugUtils.toSlug(englishName);
                helper.acquireNameLock("tasting-note-slug", "slug", baseSlug);
                String slug = helper.generateUniqueSlug(
                                englishName, tastingNoteRepository::existsBySlugIncludingDeleted);

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
                domainAudit.created(AuditAction.LOOKUP_CREATE, ENTITY, tastingNote.getId(), slug, lookupFields(reloaded, englishName));

                return buildResponse(reloaded);
        }

        @Transactional
        public TastingNoteResponseDashboard update(String slug, TastingNoteRequest request) {
                TastingNote tastingNote = tastingNoteRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(TastingNoteNotFoundException::new);

                Map<String, Object> before = lookupFields(tastingNote);

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
                        helper.assertNoDuplicateNames(
                                        request.getTranslations(),
                                        TastingNoteTranslationRequest::getLanguage,
                                        TastingNoteTranslationRequest::getName,
                                        "tasting-note",
                                        tastingNote.getId(),
                                        translationRepository::existsLiveTranslationName);
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

                domainAudit.updated(AuditAction.LOOKUP_UPDATE, ENTITY, tastingNote.getId(), tastingNote.getSlug(),
                                before, lookupFields(reloaded));

                return buildResponse(reloaded);
        }

        @Transactional
        public void delete(String slug) {
                TastingNote tastingNote = tastingNoteRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(TastingNoteNotFoundException::new);
                trashRules.refuseIfReferenced(Reference.PRODUCT_TASTING_NOTE, tastingNote.getId());

                tastingNote.softDelete();
                tastingNoteRepository.save(tastingNote);

                domainAudit.softDeleted(AuditAction.LOOKUP_DELETE, ENTITY, tastingNote.getId(), slug,
                                tastingNote.getDeletedAt());
                log.info("TastingNote soft deleted: {}", slug);
        }

        /**
         * Create-time snapshot.
         *
         * <p>The translations are written through their own repository, so the
         * entity's collection is still empty inside this transaction and a read of
         * it would record a null name. The English name the admin submitted is the
         * label the row records.
         */
        private static Map<String, Object> lookupFields(TastingNote entity, String englishName) {
                Map<String, Object> fields = lookupFields(entity);
                fields.put("name", englishName);
                return fields;
        }

        /**
         * The change-visible state of one tasting note, for an audit row: what the
         * dashboard shows an editor, and nothing only a DB admin reads.
         */
        private static Map<String, Object> lookupFields(TastingNote tastingNote) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("slug", tastingNote.getSlug());
                String name = tastingNote.getTranslations() == null ? null : tastingNote.getTranslations().stream()
                                .filter(t -> "en".equals(t.getLanguage()))
                                .map(TastingNoteTranslation::getName)
                                .findFirst().orElse(null);
                fields.put("name", name);
                return fields;
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
        @Transactional(readOnly = true)
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
                                .deletedAt((Instant) row[3])
                                .translations(new ArrayList<>())
                                .build();
        }

        private TranslationResponseDashboard buildTranslation(Object[] row) {
                return TranslationResponseDashboard.builder()
                                .language((String) row[4])
                                .name((String) row[5])


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
                helper.assertNoDuplicateNames(
                                translationRepository.findByTastingNoteId(tastingNote.getId()),
                                TastingNoteTranslation::getLanguage,
                                TastingNoteTranslation::getName,
                                "tasting-note",
                                tastingNote.getId(),
                                translationRepository::existsLiveTranslationName);
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
                trashRules.refuseUnlessTrashed(Table.TASTING_NOTES, id);
                TastingNote tastingNote = tastingNoteRepository.findDeletedById(id)
                                .orElseThrow(TastingNoteNotFoundException::new);
                tastingNoteRepository.delete(tastingNote);
                tastingNoteRepository.flush();
                domainAudit.permanentlyDeleted(ENTITY, id, tastingNote.getSlug(), tastingNote.getDeletedAt());
                log.info("TastingNote permanently deleted: {}", tastingNote.getSlug());
        }
}
