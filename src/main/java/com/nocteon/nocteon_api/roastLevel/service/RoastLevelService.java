package com.nocteon.nocteon_api.roastLevel.service;

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
import com.nocteon.nocteon_api.common.dto.TranslationResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.invalid.EntityInUseException;
import com.nocteon.nocteon_api.common.exception.notFound.RoastLevelNotFoundException;
import com.nocteon.nocteon_api.common.service.SoftDeleteRowFolder;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.common.service.TrashRules;
import com.nocteon.nocteon_api.common.service.TrashRules.Reference;
import com.nocteon.nocteon_api.common.service.TrashRules.Table;
import com.nocteon.nocteon_api.common.util.SlugUtils;
import com.nocteon.nocteon_api.roastLevel.dto.request.RoastLevelRequest;
import com.nocteon.nocteon_api.roastLevel.dto.request.RoastLevelTranslationRequest;
import com.nocteon.nocteon_api.roastLevel.dto.response.DashboardRoastLevelResponse;
import com.nocteon.nocteon_api.roastLevel.dto.response.RoastLevelResponse;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevelTranslation;
import com.nocteon.nocteon_api.roastLevel.repository.RoastLevelRepository;
import com.nocteon.nocteon_api.product.repository.CoffeeDetailsRepository;
import com.nocteon.nocteon_api.roastLevel.repository.RoastLevelTranslationRepository;
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoastLevelService {

        /** The entity_type every row for this catalogue records. */
        private static final String ENTITY = "RoastLevel";

        private final RoastLevelRepository roastLevelRepository;
        private final RoastLevelTranslationRepository translationRepository;
        private final LookupServiceHelper helper;
        private final DomainAuditRecorder domainAudit;
        private final TrashRules trashRules;
        private final CoffeeDetailsRepository coffeeDetailsRepository;

        @Transactional
        public DashboardRoastLevelResponse create(RoastLevelRequest request) {
                helper.validateTranslations(request.getTranslations());
                helper.assertNoDuplicateNames(
                                request.getTranslations(),
                                RoastLevelTranslationRequest::getLanguage,
                                RoastLevelTranslationRequest::getName,
                                "roast-level",
                                null,
                                translationRepository::existsLiveTranslationName);

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(RoastLevelTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                // Serialize slug generation for names that slugify to the same base, and account
                // for soft-deleted rows: roast-level slugs are a plain UNIQUE column, so a
                // trashed row still owns its slug even though the duplicate-name guard ignores
                // deleted rows.
                String baseSlug = SlugUtils.toSlug(englishName);
                helper.acquireNameLock("roast-level-slug", "slug", baseSlug);
                String slug = helper.generateUniqueSlug(
                                englishName, roastLevelRepository::existsBySlugIncludingDeleted);

                RoastLevel roastLevel = RoastLevel.builder()
                                .slug(slug)
                                .color(request.getColor())
                                .build();

                roastLevel = roastLevelRepository.save(roastLevel);

                final RoastLevel saved = roastLevel;
                List<RoastLevelTranslation> translations = new ArrayList<>();
                for (RoastLevelTranslationRequest t : request.getTranslations()) {
                        translations.add(RoastLevelTranslation.builder()
                                        .roastLevel(saved)
                                        .language(t.getLanguage())
                                        .name(t.getName())
                                        .description(t.getDescription())
                                        .build());
                }
                translationRepository.saveAll(translations);

                var reloaded = roastLevelRepository.findBySlugWithTranslations(slug).orElse(roastLevel);


                domainAudit.created(AuditAction.LOOKUP_CREATE, ENTITY, roastLevel.getId(), slug, lookupFields(reloaded, englishName));

                return buildResponse(reloaded);
        }

        @Transactional
        public DashboardRoastLevelResponse update(String slug, RoastLevelRequest request) {
                RoastLevel roastLevel = roastLevelRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(RoastLevelNotFoundException::new);

                Map<String, Object> before = lookupFields(roastLevel);
                
                roastLevel.setColor(request.getColor());

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
                        helper.assertNoDuplicateNames(
                                        request.getTranslations(),
                                        RoastLevelTranslationRequest::getLanguage,
                                        RoastLevelTranslationRequest::getName,
                                        "roast-level",
                                        roastLevel.getId(),
                                        translationRepository::existsLiveTranslationName);
                        request.getTranslations().forEach(t -> translationRepository
                                        .findByRoastLevelIdAndLanguage(roastLevel.getId(), t.getLanguage())
                                        .ifPresentOrElse(
                                                        existing -> {
                                                                existing.setName(t.getName());
                                                                existing.setDescription(t.getDescription());
                                                                translationRepository.save(existing);
                                                        },
                                                        () -> translationRepository.save(
                                                                        RoastLevelTranslation.builder()
                                                                                        .roastLevel(roastLevel)
                                                                                        .language(t.getLanguage())
                                                                                        .name(t.getName())
                                                                                        .description(t.getDescription())
                                                                                        .build())));
                }

                roastLevelRepository.save(roastLevel);
                var reloaded = roastLevelRepository.findBySlugWithTranslations(roastLevel.getSlug()).orElse(roastLevel);

                domainAudit.updated(AuditAction.LOOKUP_UPDATE, ENTITY, roastLevel.getId(), roastLevel.getSlug(),
                                before, lookupFields(reloaded));

                return buildResponse(reloaded);
        }

        @Transactional
        public void delete(String slug) {
                RoastLevel roastLevel = roastLevelRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(RoastLevelNotFoundException::new);
                trashRules.refuseIfReferenced(Reference.COFFEE_DETAIL_ROAST_LEVEL, roastLevel.getId());

                roastLevel.softDelete();
                roastLevelRepository.save(roastLevel);

                domainAudit.softDeleted(AuditAction.LOOKUP_DELETE, ENTITY, roastLevel.getId(), slug,
                                roastLevel.getDeletedAt());
        }

        public RoastLevelResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                RoastLevel roastLevel = roastLevelRepository.findBySlugAndLanguage(slug, language)
                                .orElseThrow(RoastLevelNotFoundException::new);
                return buildResponse(roastLevel, language);
        }

        public PageResponse<RoastLevelResponse> getAll(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Page<RoastLevel> page = roastLevelRepository.findAllPublic(
                                SearchPatterns.like(filter.getSearch()),
                                language, filter.toPageable());
                return PageResponse.of(page.map(r -> buildResponse(r, language)));
        }

        @Transactional(readOnly = true)
        public DashboardRoastLevelResponse getDashboardBySlug(String slug) {
                RoastLevel roastLevel = roastLevelRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(RoastLevelNotFoundException::new);
                return buildResponse(roastLevel);
        }

        @Transactional(readOnly = true)
        public PageResponse<DashboardRoastLevelResponse> getAllDashboard(LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());
                Page<RoastLevel> page = roastLevelRepository.findAllDashboard(
                                search,
                               filter.toPageable());
                return PageResponse.of(page.map(this::buildResponse));
        }

        /**
         * Create-time snapshot.
         *
         * <p>The translations are written through their own repository, so the
         * entity's collection is still empty inside this transaction and a read of
         * it would record a null name. The English name the admin submitted is the
         * label the row records.
         */
        private static Map<String, Object> lookupFields(RoastLevel entity, String englishName) {
                Map<String, Object> fields = lookupFields(entity);
                fields.put("name", englishName);
                return fields;
        }

        /**
         * The change-visible state of one roast level, for an audit row: what the
         * dashboard shows an editor, and nothing only a DB admin reads.
         */
        private static Map<String, Object> lookupFields(RoastLevel roastLevel) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("slug", roastLevel.getSlug());
                fields.put("color", roastLevel.getColor());
                String name = roastLevel.getTranslations() == null ? null : roastLevel.getTranslations().stream()
                                .filter(t -> "en".equals(t.getLanguage()))
                                .map(RoastLevelTranslation::getName)
                                .findFirst().orElse(null);
                fields.put("name", name);
                return fields;
        }

        private RoastLevelResponse buildResponse(RoastLevel roastLevel, String language) {
                List<RoastLevelTranslation> translations = translationRepository
                                .findByRoastLevelId(roastLevel.getId());

                RoastLevelTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                return RoastLevelResponse.builder()
                                .id(roastLevel.getId())
                                .slug(roastLevel.getSlug())
                                .color(roastLevel.getColor())
                                .name(translation != null ? translation.getName() : null)
                                .description(translation != null ? translation.getDescription() : null)
                                .build();
        }

        private DashboardRoastLevelResponse buildResponse(RoastLevel roastLevel) {
                return DashboardRoastLevelResponse.builder()
                                .id(roastLevel.getId())
                                .slug(roastLevel.getSlug())
                                .color(roastLevel.getColor())
                                .createdAt(roastLevel.getCreatedAt())
                                .updatedAt(roastLevel.getUpdatedAt())
                                .translations(
                                                roastLevel.getTranslations()
                                                                .stream()
                                                                .map(t -> TranslationResponse.builder()
                                                                                .language(t.getLanguage())
                                                                                .name(t.getName())
                                                                                .description(t.getDescription())
                                                                                .build())
                                                                .toList())
                                .build();
        }

        /**
         * Trashed RoastLevels for the dashboard's deleted-items table.
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
        public PageResponse<DashboardRoastLevelResponse> getAllDeleted(LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());
                int pageNum = filter.getPage();
                int size = filter.getSize();
                String sort = filter.getSort();
                String direction = filter.getDirection();

                List<Object[]> rawResults = roastLevelRepository.findDeletedBySearchNative(search);

                List<DashboardRoastLevelResponse> allResults = SoftDeleteRowFolder.fold(
                                rawResults,
                                row -> ((Number) row[0]).longValue(),
                                this::mapToDashboardRoastLevelResponse,
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
                List<DashboardRoastLevelResponse> pagedResults = allResults.subList(fromIndex, toIndex);

                Page<DashboardRoastLevelResponse> page = new org.springframework.data.domain.PageImpl<>(
                                pagedResults,
                                org.springframework.data.domain.PageRequest.of(pageNum, size),
                                allResults.size());
                return PageResponse.of(page);
        }

        private DashboardRoastLevelResponse mapToDashboardRoastLevelResponse(Object[] row) {
                return DashboardRoastLevelResponse.builder()
                                .id(((Number) row[0]).longValue())
                                .slug((String) row[1])
                                .color((String) row[2])
                                .createdAt((Instant) row[3])
                                .updatedAt((Instant) row[4])
                                .deletedAt((Instant) row[5])
                                .translations(new ArrayList<>())
                                .build();
        }

        private TranslationResponse buildTranslation(Object[] row) {
                return TranslationResponse.builder()
                                .language((String) row[6])
                                .name((String) row[7])
                                .description((String) row[8])

                                .build();
        }

        /**
         * Un-delete a RoastLevel.
         *
         * <p>Reads through {@code findDeletedById}, so an id that is live or unknown raises
         * {@link RoastLevelNotFoundException} rather than reporting a restore that changed nothing.
         */
        @Transactional
        public void restore(Long id) {
                RoastLevel roastLevel = roastLevelRepository.findDeletedById(id)
                                .orElseThrow(RoastLevelNotFoundException::new);
                helper.assertNoDuplicateNames(
                                translationRepository.findByRoastLevelId(roastLevel.getId()),
                                RoastLevelTranslation::getLanguage,
                                RoastLevelTranslation::getName,
                                "roast-level",
                                roastLevel.getId(),
                                translationRepository::existsLiveTranslationName);
                roastLevel.setDeletedAt(null);
                roastLevelRepository.save(roastLevel);
                log.info("RoastLevel restored: {}", roastLevel.getSlug());
        }

        /**
         * Permanently deletes a trashed RoastLevel: removes the row itself (it carries no media, so there is nothing to clean on Cloudinary).
         *
         * <p>Trash-only: reads through {@code findDeletedById}, so a live or unknown id raises
         * {@link RoastLevelNotFoundException} instead of deleting anything. Soft-delete first — this never touches live
         * rows.
         */
        @Transactional
        public void permanentDelete(Long id) {
                trashRules.refuseUnlessTrashed(Table.ROAST_LEVELS, id);
                RoastLevel roastLevel = roastLevelRepository.findDeletedById(id)
                                .orElseThrow(RoastLevelNotFoundException::new);
                roastLevelRepository.delete(roastLevel);
                roastLevelRepository.flush();
                domainAudit.permanentlyDeleted(ENTITY, id, roastLevel.getSlug(), roastLevel.getDeletedAt());
                log.info("RoastLevel permanently deleted: {}", roastLevel.getSlug());
        }
}
