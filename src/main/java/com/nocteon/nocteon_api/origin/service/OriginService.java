package com.nocteon.nocteon_api.origin.service;

import java.time.Instant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.service.DomainAuditRecorder;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.dto.TranslationResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.invalid.EntityInUseException;
import com.nocteon.nocteon_api.common.exception.notFound.OriginNotFoundException;
import com.nocteon.nocteon_api.common.service.SoftDeleteRowFolder;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.common.service.TrashRules;
import com.nocteon.nocteon_api.common.service.TrashRules.Reference;
import com.nocteon.nocteon_api.common.service.TrashRules.Table;
import com.nocteon.nocteon_api.common.transaction.AfterCommit;
import com.nocteon.nocteon_api.common.util.SlugUtils;
import com.nocteon.nocteon_api.origin.dto.request.OriginRequest;
import com.nocteon.nocteon_api.origin.dto.request.OriginTranslationRequest;
import com.nocteon.nocteon_api.origin.dto.response.DashboardOriginResponse;
import com.nocteon.nocteon_api.origin.dto.response.OriginOptionResponse;
import com.nocteon.nocteon_api.origin.dto.response.OriginResponse;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.entity.OriginTranslation;
import com.nocteon.nocteon_api.origin.repository.OriginRepository;
import com.nocteon.nocteon_api.origin.repository.OriginTranslationRepository;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.enums.MediaType;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OriginService {

        /** The entity_type every row for this catalogue records. */
        private static final String ENTITY = "Origin";

        private final OriginRepository originRepository;
        private final OriginTranslationRepository translationRepository;
        private final LookupServiceHelper helper;
        private final ProductResponseMapper productResponseMapper;
        private final ProductRepository productRepository;
        private final DomainAuditRecorder domainAudit;
        private final TrashRules trashRules;

        @Transactional(readOnly = true)
        public Page<ProductCardResponse> getProductsByOriginSlug(String slug, Pageable pageable) {
                if (!originRepository.existsBySlug(slug)) {
                        throw new OriginNotFoundException();
                }
                String language = LocaleContextHolder.getLocale().getLanguage();
                return productRepository.findByOriginSlugPublic(slug, pageable)
                                .map(p -> productResponseMapper.buildListResponse(p, language));
        }

        @Transactional
        public DashboardOriginResponse create(OriginRequest request, MultipartFile image) {
                helper.validateTranslations(request.getTranslations());
                helper.assertNoDuplicateNames(
                                request.getTranslations(),
                                OriginTranslationRequest::getLanguage,
                                OriginTranslationRequest::getName,
                                "origin",
                                null,
                                translationRepository::existsLiveTranslationName);

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(OriginTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                // Serialize slug generation for names that slugify to the same base, and account
                // for soft-deleted rows: origins.slug is a plain UNIQUE column, so a trashed row
                // still owns its slug even though the duplicate-name guard ignores deleted rows.
                String baseSlug = SlugUtils.toSlug(englishName);
                helper.acquireNameLock("origin-slug", "slug", baseSlug);
                String slug = helper.generateUniqueSlug(
                                englishName, originRepository::existsBySlugIncludingDeleted);
                String imageUrl = helper.uploadMedia(image, "origins", MediaType.IMAGE);

                Origin origin = Origin.builder()
                                .slug(slug)
                                .code(request.getCode())
                                .imageUrl(imageUrl)
                                .build();

                origin = originRepository.save(origin);

                final Origin saved = origin;
                List<OriginTranslation> translations = new ArrayList<>();
                for (OriginTranslationRequest t : request.getTranslations()) {
                        translations.add(OriginTranslation.builder()
                                        .origin(saved)
                                        .language(t.getLanguage())
                                        .name(t.getName())
                                        .description(t.getDescription())
                                        .build());
                }
                translationRepository.saveAll(translations);

                var reloaded = originRepository.findBySlugWithTranslations(slug).orElse(origin);
                domainAudit.created(AuditAction.LOOKUP_CREATE, ENTITY, origin.getId(), slug, lookupFields(reloaded, englishName));

                log.info("Origin created with slug: {}", slug);
                return buildResponse(reloaded);
        }

        @Transactional
        public DashboardOriginResponse update(String slug, OriginRequest request, MultipartFile image) {
                Origin origin = originRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(OriginNotFoundException::new);

                Map<String, Object> before = lookupFields(origin);

                if (request.getCode() != null)
                        origin.setCode(request.getCode());

                if (image != null && !image.isEmpty()) {
                        helper.deleteMediaIfExists(origin.getImageUrl());
                        origin.setImageUrl(helper.uploadMedia(image, "origins", MediaType.IMAGE));
                }

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
                        helper.assertNoDuplicateNames(
                                        request.getTranslations(),
                                        OriginTranslationRequest::getLanguage,
                                        OriginTranslationRequest::getName,
                                        "origin",
                                        origin.getId(),
                                        translationRepository::existsLiveTranslationName);
                        request.getTranslations().forEach(t -> translationRepository
                                        .findByOriginIdAndLanguage(origin.getId(), t.getLanguage())
                                        .ifPresentOrElse(
                                                        existing -> {
                                                                existing.setName(t.getName());
                                                                existing.setDescription(t.getDescription());
                                                                translationRepository.save(existing);
                                                        },
                                                        () -> translationRepository.save(
                                                                        OriginTranslation.builder()
                                                                                        .origin(origin)
                                                                                        .language(t.getLanguage())
                                                                                        .name(t.getName())
                                                                                        .description(t.getDescription())
                                                                                        .build())));
                }

                originRepository.save(origin);
                var reloaded = originRepository.findBySlugWithTranslations(origin.getSlug()).orElse(origin);

                domainAudit.updated(AuditAction.LOOKUP_UPDATE, ENTITY, origin.getId(), origin.getSlug(),
                                before, lookupFields(reloaded));

                return buildResponse(reloaded);
        }

        @Transactional
        public DashboardOriginResponse uploadImage(String slug, MultipartFile file) {
                Origin origin = originRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(OriginNotFoundException::new);

                helper.deleteMediaIfExists(origin.getImageUrl());
                origin.setImageUrl(helper.uploadMedia(file, "origins", MediaType.IMAGE));
                originRepository.save(origin);

                var reloaded = originRepository.findBySlugWithTranslations(slug).orElse(origin);


                return buildResponse(reloaded);
        }

        @Transactional
        public void delete(String slug) {
                Origin origin = originRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(OriginNotFoundException::new);
                trashRules.refuseIfReferenced(Reference.PRODUCT_ORIGIN, origin.getId());
                trashRules.refuseIfReferenced(Reference.FARM_ORIGIN, origin.getId());

                origin.softDelete();
                originRepository.save(origin);

                domainAudit.softDeleted(AuditAction.LOOKUP_DELETE, ENTITY, origin.getId(), slug,
                                origin.getDeletedAt());

                log.info("Origin soft deleted: {}", slug);
        }

        /**
         * Create-time snapshot.
         *
         * <p>The translations are written through their own repository, so the
         * entity's collection is still empty inside this transaction and a read of
         * it would record a null name. The English name the admin submitted is the
         * label the row records.
         */
        private static Map<String, Object> lookupFields(Origin entity, String englishName) {
                Map<String, Object> fields = lookupFields(entity);
                fields.put("name", englishName);
                return fields;
        }

        /**
         * The change-visible state of one origin, for an audit row: what the dashboard
         * shows an editor, and nothing only a DB admin reads.
         */
        private static Map<String, Object> lookupFields(Origin origin) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("slug", origin.getSlug());
                fields.put("code", origin.getCode());
                fields.put("imageUrl", origin.getImageUrl());
                String name = origin.getTranslations() == null ? null : origin.getTranslations().stream()
                                .filter(t -> "en".equals(t.getLanguage()))
                                .map(OriginTranslation::getName)
                                .findFirst().orElse(null);
                fields.put("name", name);
                return fields;
        }

        public OriginResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Origin origin = originRepository.findBySlugAndLanguage(slug, language)
                                .orElseThrow(OriginNotFoundException::new);
                return buildResponse(origin, language);
        }

        public List<OriginOptionResponse> getOptions() {
                String language = LocaleContextHolder.getLocale().getLanguage();
                return originRepository.findAllForOptions().stream()
                                .map(o -> {
                                        String name = o.getTranslations().stream()
                                                        .filter(t -> t.getLanguage().equals(language))
                                                        .findFirst()
                                                        .map(OriginTranslation::getName)
                                                        .orElse(o.getSlug());
                                        return OriginOptionResponse.builder()
                                                        .slug(o.getSlug())
                                                        .name(name)
                                                        .build();
                                })
                                .toList();
        }

        @Transactional(readOnly = true)
        public DashboardOriginResponse getDashboardBySlug(String slug) {
                Origin origin = originRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(OriginNotFoundException::new);
                return buildResponse(origin);
        }

        public OriginResponse getOrigin(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Origin origin = originRepository.findBySlugAndLanguage(slug, language)
                                .orElseThrow(OriginNotFoundException::new);
                return buildResponse(origin, language);
        }

        public PageResponse<OriginResponse> getAll(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Page<Origin> page = originRepository.findAllPublic(
                                SearchPatterns.like(filter.getSearch()),
                                language,
                                filter.toPageable());
                return PageResponse.of(page.map(o -> buildResponse(o, language)));
        }

        @Transactional(readOnly = true)
        public PageResponse<DashboardOriginResponse> getAllDashboard(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                String search = SearchPatterns.like(filter.getSearch());

                Page<Origin> page = originRepository.findAllDashboard(
                                search,
                                language,
                                filter.toPageable());
                return PageResponse.of(
                                page.map(this::buildResponse));
        }

        private OriginResponse buildResponse(Origin origin, String language) {
                List<OriginTranslation> translations = translationRepository
                                .findByOriginId(origin.getId());

                OriginTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                return OriginResponse.builder()
                                .id(origin.getId())
                                .slug(origin.getSlug())
                                .code(origin.getCode())
                                .createdAt(origin.getCreatedAt())
                                .name(translation != null ? translation.getName() : null)
                                .description(translation != null ? translation.getDescription() : null)
                                .imageUrl(origin.getImageUrl())
                                .build();
        }

        private DashboardOriginResponse buildResponse(Origin origin) {
                return DashboardOriginResponse.builder()
                                .id(origin.getId())
                                .code(origin.getCode())
                                .slug(origin.getSlug())
                                .imageUrl(origin.getImageUrl())
                                .createdAt(origin.getCreatedAt())
                                .translations(
                                                origin.getTranslations()
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
         * Trashed Origins for the dashboard's deleted-items table.
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
        public PageResponse<DashboardOriginResponse> getAllDeleted(LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());
                int pageNum = filter.getPage();
                int size = filter.getSize();
                String sort = filter.getSort();
                String direction = filter.getDirection();

                List<Object[]> rawResults = originRepository.findDeletedBySearchNative(search);

                List<DashboardOriginResponse> allResults = SoftDeleteRowFolder.fold(
                                rawResults,
                                row -> ((Number) row[0]).longValue(),
                                this::mapToDashboardOriginResponse,
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
                List<DashboardOriginResponse> pagedResults = allResults.subList(fromIndex, toIndex);

                Page<DashboardOriginResponse> page = new org.springframework.data.domain.PageImpl<>(
                                pagedResults,
                                org.springframework.data.domain.PageRequest.of(pageNum, size),
                                allResults.size());
                return PageResponse.of(page);
        }

        private DashboardOriginResponse mapToDashboardOriginResponse(Object[] row) {
                return DashboardOriginResponse.builder()
                                .id(((Number) row[0]).longValue())
                                .slug((String) row[1])
                                .code((String) row[2])
                                .imageUrl((String) row[3])
                                .createdAt((Instant) row[4])
                                .translations(new ArrayList<>())
                                .build();
        }

        private TranslationResponse buildTranslation(Object[] row) {
                return TranslationResponse.builder()
                                .language((String) row[5])
                                .name((String) row[6])
                                .description((String) row[7])

                                .build();
        }

        /**
         * Un-delete a Origin.
         *
         * <p>Reads through {@code findDeletedById}, so an id that is live or unknown raises
         * {@link OriginNotFoundException} rather than reporting a restore that changed nothing.
         */
        @Transactional
        public void restore(Long id) {
                Origin origin = originRepository.findDeletedById(id)
                                .orElseThrow(OriginNotFoundException::new);
                helper.assertNoDuplicateNames(
                                translationRepository.findByOriginId(origin.getId()),
                                OriginTranslation::getLanguage,
                                OriginTranslation::getName,
                                "origin",
                                origin.getId(),
                                translationRepository::existsLiveTranslationName);
                origin.setDeletedAt(null);
                originRepository.save(origin);
                log.info("Origin restored: {}", origin.getSlug());
        }

        /**
         * Permanently deletes a trashed Origin: removes its image from Cloudinary, then the row itself.
         *
         * <p>Deletes the row — and flushes, so a foreign-key conflict surfaces here — <em>before</em>
         * touching Cloudinary. If the database refuses the delete, the trashed row and its image
         * are both untouched and the admin can retry; the reverse order would strand a restored
         * item without its picture.
         *
         * <p>Trash-only: reads through {@code findDeletedById}, so a live or unknown id raises
         * {@link OriginNotFoundException} instead of deleting anything. Soft-delete first — this never touches live
         * rows.
         */
        @Transactional
        public void permanentDelete(Long id) {
                trashRules.refuseUnlessTrashed(Table.ORIGINS, id);
                Origin origin = originRepository.findDeletedById(id)
                                .orElseThrow(OriginNotFoundException::new);
                originRepository.delete(origin);
                originRepository.flush();
                String imageUrl = origin.getImageUrl();
                String slug = origin.getSlug();
                AfterCommit.run(() -> {
                    try {
                        helper.deleteMediaIfExists(imageUrl);
                    } catch (Exception e) {
                        log.warn("Cloudinary delete failed for origin {} ({}): {}", slug, imageUrl, e.getMessage());
                    }
                });
                domainAudit.permanentlyDeleted(ENTITY, id, origin.getSlug(), origin.getDeletedAt());
                log.info("Origin permanently deleted: {}", slug);
        }
}
