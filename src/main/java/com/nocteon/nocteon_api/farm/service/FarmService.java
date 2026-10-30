package com.nocteon.nocteon_api.farm.service;

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
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.invalid.EntityInUseException;
import com.nocteon.nocteon_api.common.exception.notFound.FarmNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.OriginNotFoundException;
import com.nocteon.nocteon_api.common.service.SoftDeleteRowFolder;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.common.service.TrashRules;
import com.nocteon.nocteon_api.common.service.TrashRules.Reference;
import com.nocteon.nocteon_api.common.service.TrashRules.Table;
import com.nocteon.nocteon_api.common.transaction.AfterCommit;
import com.nocteon.nocteon_api.common.util.SlugUtils;
import com.nocteon.nocteon_api.farm.dto.request.FarmFilterRequest;
import com.nocteon.nocteon_api.farm.dto.request.FarmRequest;
import com.nocteon.nocteon_api.farm.dto.request.FarmTranslationRequest;
import com.nocteon.nocteon_api.farm.dto.response.DashboardFarmResponse;
import com.nocteon.nocteon_api.farm.dto.response.FarmResponse;
import com.nocteon.nocteon_api.farm.dto.response.FarmTranslationResponse;
import com.nocteon.nocteon_api.farm.entity.Farm;
import com.nocteon.nocteon_api.farm.entity.FarmTranslation;
import com.nocteon.nocteon_api.farm.repository.FarmRepository;
import com.nocteon.nocteon_api.farm.repository.FarmTranslationRepository;

import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.repository.OriginRepository;
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
public class FarmService {

        /** The entity_type every row for this catalogue records. */
        private static final String ENTITY = "Farm";

        private final ProductRepository productRepository;
        private final FarmRepository farmRepository;
        private final FarmTranslationRepository farmTranslationRepository;
        private final OriginRepository originRepository;
        private final LookupServiceHelper helper;
        private final DomainAuditRecorder domainAudit;
        private final TrashRules trashRules;
        private final ProductResponseMapper productResponseMapper;

        @Transactional(readOnly = true)
        public Page<ProductCardResponse> getProductsByFarmSlug(String slug, Pageable pageable) {
                if (!farmRepository.existsBySlug(slug)) {
                        throw new FarmNotFoundException();
                }
                String language = LocaleContextHolder.getLocale().getLanguage();
                return productRepository.findByFarmSlugPublic(slug, pageable)
                                .map(p -> productResponseMapper.buildListResponse(p, language));
        }

        public PageResponse<FarmResponse> getAll(FarmFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                String search = SearchPatterns.like(filter.getSearch());

                Page<Farm> page = farmRepository.findAllPublic(
                                language,
                                search,
                                filter.getOriginSlug(),
                                filter.toPageable());
                return PageResponse.of(page.map(f -> buildResponse(f, language)));
        }

        @Transactional(readOnly = true)
        public PageResponse<DashboardFarmResponse> getAllDashboard(FarmFilterRequest filter) {

                String search = SearchPatterns.like(filter.getSearch());
                Page<Farm> page = farmRepository.findAllDashboard(
                                search,
                                filter.getOriginSlug(),
                                filter.toPageable());
                return PageResponse.of(page.map(this::buildResponse));
        }

        public FarmResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Farm farm = farmRepository.findBySlugAndLanguage(slug, language)
                                .orElseThrow(FarmNotFoundException::new);
                return buildResponse(farm, language);
        }

        @Transactional
        public DashboardFarmResponse create(FarmRequest request, MultipartFile image) {
                helper.validateTranslations(request.getTranslations());
                helper.assertNoDuplicateNames(
                                request.getTranslations(),
                                FarmTranslationRequest::getLanguage,
                                FarmTranslationRequest::getName,
                                "farm",
                                null,
                                farmTranslationRepository::existsLiveTranslationName);

                Origin origin = originRepository.findBySlug(request.getOriginSlug())
                                .orElseThrow(OriginNotFoundException::new);

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(FarmTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                // Serialize slug generation for names that slugify to the same base, and account
                // for soft-deleted rows: farms.slug is a plain UNIQUE column, so a trashed row
                // still owns its slug even though the duplicate-name guard ignores deleted rows.
                String baseSlug = SlugUtils.toSlug(englishName);
                helper.acquireNameLock("farm-slug", "slug", baseSlug);
                String slug = helper.generateUniqueSlug(
                                englishName, farmRepository::existsBySlugIncludingDeleted);
                String imageUrl = helper.uploadMedia(image, "farms", MediaType.IMAGE);

                Farm farm = Farm.builder()
                                .origin(origin)
                                .slug(slug)
                                .imageUrl(imageUrl)
                                .build();

                farm = farmRepository.save(farm);

                final Farm saved = farm;
                List<FarmTranslation> translations = new ArrayList<>();
                for (FarmTranslationRequest t : request.getTranslations()) {
                        translations.add(FarmTranslation.builder()
                                        .farm(saved)
                                        .language(t.getLanguage())
                                        .name(t.getName())
                                        .country(t.getCountry())
                                        .description(t.getDescription())
                                        .build());
                }
                farmTranslationRepository.saveAll(translations);

                log.info("Farm created with slug: {}", slug);
                var reloaded = farmRepository.findBySlugWithTranslations(slug).orElse(farm);

                domainAudit.created(AuditAction.LOOKUP_CREATE, ENTITY, farm.getId(), slug, lookupFields(reloaded, englishName));

                return buildResponse(reloaded);
        }

        @Transactional(readOnly = true)
        public DashboardFarmResponse getDashboardBySlug(String slug) {
                Farm farm = farmRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(FarmNotFoundException::new);
                return buildResponse(farm);
        }

        @Transactional
        public DashboardFarmResponse update(String slug, FarmRequest request, MultipartFile image) {
                Farm farm = farmRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(FarmNotFoundException::new);

                Map<String, Object> before = lookupFields(farm);

                if (request.getOriginSlug() != null) {
                        Origin origin = originRepository.findBySlug(request.getOriginSlug())
                                        .orElseThrow(OriginNotFoundException::new);
                        farm.setOrigin(origin);
                }

                if (image != null && !image.isEmpty()) {
                        helper.deleteMediaIfExists(farm.getImageUrl());
                        farm.setImageUrl(helper.uploadMedia(image, "farms", MediaType.IMAGE));
                }

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
                        helper.assertNoDuplicateNames(
                                        request.getTranslations(),
                                        FarmTranslationRequest::getLanguage,
                                        FarmTranslationRequest::getName,
                                        "farm",
                                        farm.getId(),
                                        farmTranslationRepository::existsLiveTranslationName);
                        request.getTranslations().forEach(t -> farmTranslationRepository
                                        .findByFarmIdAndLanguage(farm.getId(), t.getLanguage())
                                        .ifPresentOrElse(
                                                        existing -> {
                                                                existing.setName(t.getName());
                                                                existing.setCountry(t.getCountry());
                                                                existing.setDescription(t.getDescription());
                                                                farmTranslationRepository.save(existing);
                                                        },
                                                        () -> farmTranslationRepository.save(
                                                                        FarmTranslation.builder()
                                                                                        .farm(farm)
                                                                                        .language(t.getLanguage())
                                                                                        .name(t.getName())
                                                                                        .country(t.getCountry())
                                                                                        .description(t.getDescription())
                                                                                        .build())));
                }

                farmRepository.save(farm);
                var reloaded = farmRepository.findBySlugWithTranslations(farm.getSlug()).orElse(farm);

                domainAudit.updated(AuditAction.LOOKUP_UPDATE, ENTITY, farm.getId(), farm.getSlug(),
                                before, lookupFields(reloaded));

                return buildResponse(reloaded);
        }

        @Transactional
        public DashboardFarmResponse uploadImage(String slug, MultipartFile file) {
                Farm farm = farmRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(FarmNotFoundException::new);

                helper.deleteMediaIfExists(farm.getImageUrl());
                farm.setImageUrl(helper.uploadMedia(file, "farms", MediaType.IMAGE));
                farmRepository.save(farm);

                var reloaded = farmRepository.findBySlugWithTranslations(slug).orElse(farm);


                return buildResponse(reloaded);
        }

        @Transactional
        public void delete(String slug) {
                Farm farm = farmRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(FarmNotFoundException::new);
                trashRules.refuseIfReferenced(Reference.PRODUCT_FARM, farm.getId());

                farm.softDelete();
                farmRepository.save(farm);

                domainAudit.softDeleted(AuditAction.LOOKUP_DELETE, ENTITY, farm.getId(), slug,
                                farm.getDeletedAt());
                log.info("Farm soft deleted: {}", slug);
        }

        /**
         * Create-time snapshot.
         *
         * <p>The translations are written through their own repository, so the
         * entity's collection is still empty inside this transaction and a read of
         * it would record a null name. The English name the admin submitted is the
         * label the row records.
         */
        private static Map<String, Object> lookupFields(Farm entity, String englishName) {
                Map<String, Object> fields = lookupFields(entity);
                fields.put("name", englishName);
                return fields;
        }

        /**
         * The change-visible state of one farm, for an audit row: what the
         * dashboard shows an editor, and nothing only a DB admin reads.
         */
        private static Map<String, Object> lookupFields(Farm farm) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("slug", farm.getSlug());
                fields.put("imageUrl", farm.getImageUrl());
                fields.put("originSlug", farm.getOrigin() == null ? null : farm.getOrigin().getSlug());
                String name = farm.getTranslations() == null ? null : farm.getTranslations().stream()
                                .filter(t -> "en".equals(t.getLanguage()))
                                .map(FarmTranslation::getName)
                                .findFirst().orElse(null);
                fields.put("name", name);
                return fields;
        }

        private FarmResponse buildResponse(Farm farm, String language) {
                List<FarmTranslation> translations = farm.getTranslations();

                FarmTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                return FarmResponse.builder()
                                .id(farm.getId())
                                .originSlug(farm.getOrigin().getSlug())
                                .slug(farm.getSlug())
                                .name(translation != null ? translation.getName() : null)
                                .country(translation != null ? translation.getCountry() : null)
                                .description(translation != null ? translation.getDescription() : null)
                                .imageUrl(farm.getImageUrl())
                                .createdAt(farm.getCreatedAt())
                                .build();
        }

        private DashboardFarmResponse buildResponse(Farm farm) {
                return DashboardFarmResponse.builder()
                                .id(farm.getId())
                                .originSlug(farm.getOrigin().getSlug())
                                .slug(farm.getSlug())
                                .imageUrl(farm.getImageUrl())
                                .createdAt(farm.getCreatedAt())
                                .translations(
                                                farm.getTranslations()
                                                                .stream()
                                                                .map(t -> FarmTranslationResponse.builder()
                                                                                .language(t.getLanguage())
                                                                                .name(t.getName())
                                                                                .description(t.getDescription())
                                                                                .country(t.getCountry())
                                                                                .build())
                                                                .toList())
                                .build();
        }

        /**
         * Trashed Farms for the dashboard's deleted-items table.
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
        public PageResponse<DashboardFarmResponse> getAllDeleted(LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());
                int pageNum = filter.getPage();
                int size = filter.getSize();
                String sort = filter.getSort();
                String direction = filter.getDirection();

                List<Object[]> rawResults = farmRepository.findDeletedBySearchNative(search);

                List<DashboardFarmResponse> allResults = SoftDeleteRowFolder.fold(
                                rawResults,
                                row -> ((Number) row[0]).longValue(),
                                this::mapToDashboardFarmResponse,
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
                List<DashboardFarmResponse> pagedResults = allResults.subList(fromIndex, toIndex);

                Page<DashboardFarmResponse> page = new org.springframework.data.domain.PageImpl<>(
                                pagedResults,
                                org.springframework.data.domain.PageRequest.of(pageNum, size),
                                allResults.size());
                return PageResponse.of(page);
        }

        private DashboardFarmResponse mapToDashboardFarmResponse(Object[] row) {
                return DashboardFarmResponse.builder()
                                .id(((Number) row[0]).longValue())
                                .slug((String) row[1])
                                .imageUrl((String) row[2])
                                .createdAt((Instant) row[3])
                                .deletedAt((Instant) row[4])
                                .originSlug((String) row[5])
                                .translations(new ArrayList<>())
                                .build();
        }

        private FarmTranslationResponse buildTranslation(Object[] row) {
                return FarmTranslationResponse.builder()
                                .language((String) row[6])
                                .name((String) row[7])
                                .description((String) row[9])
                                .country((String) row[8])
                                .build();
        }

        /**
         * Un-delete a Farm.
         *
         * <p>Reads through {@code findDeletedById}, so an id that is live or unknown raises
         * {@link FarmNotFoundException} rather than reporting a restore that changed nothing.
         */
        @Transactional
        public void restore(Long id) {
                Farm farm = farmRepository.findDeletedById(id)
                                .orElseThrow(FarmNotFoundException::new);
                helper.assertNoDuplicateNames(
                                farmTranslationRepository.findByFarmId(farm.getId()),
                                FarmTranslation::getLanguage,
                                FarmTranslation::getName,
                                "farm",
                                farm.getId(),
                                farmTranslationRepository::existsLiveTranslationName);
                farm.setDeletedAt(null);
                farmRepository.save(farm);
                log.info("Farm restored: {}", farm.getSlug());
        }

        /**
         * Permanently deletes a trashed Farm: removes its image from Cloudinary, then the row itself.
         *
         * <p>Deletes the row — and flushes, so a foreign-key conflict surfaces here — <em>before</em>
         * touching Cloudinary. If the database refuses the delete, the trashed row and its image
         * are both untouched and the admin can retry; the reverse order would strand a restored
         * item without its picture.
         *
         * <p>Trash-only: reads through {@code findDeletedById}, so a live or unknown id raises
         * {@link FarmNotFoundException} instead of deleting anything. Soft-delete first — this never touches live
         * rows.
         */
        @Transactional
        public void permanentDelete(Long id) {
                trashRules.refuseUnlessTrashed(Table.FARMS, id);
                Farm farm = farmRepository.findDeletedById(id)
                                .orElseThrow(FarmNotFoundException::new);
                farmRepository.delete(farm);
                farmRepository.flush();
                String imageUrl = farm.getImageUrl();
                String slug = farm.getSlug();
                AfterCommit.run(() -> {
                    try {
                        helper.deleteMediaIfExists(imageUrl);
                    } catch (Exception e) {
                        log.warn("Cloudinary delete failed for farm {} ({}): {}", slug, imageUrl, e.getMessage());
                    }
                });
                domainAudit.permanentlyDeleted(ENTITY, id, farm.getSlug(), farm.getDeletedAt());
                log.info("Farm permanently deleted: {}", slug);
        }
}
