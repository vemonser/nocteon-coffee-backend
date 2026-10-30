package com.nocteon.nocteon_api.pairing.service;

import java.time.Instant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.service.DomainAuditRecorder;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.dto.TranslationResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.PairingNotFoundException;
import com.nocteon.nocteon_api.common.service.SoftDeleteRowFolder;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.common.util.SlugUtils;
import com.nocteon.nocteon_api.common.transaction.AfterCommit;
import com.nocteon.nocteon_api.pairing.dto.request.PairingRequest;
import com.nocteon.nocteon_api.pairing.dto.request.PairingTranslationRequest;
import com.nocteon.nocteon_api.pairing.dto.response.PairingResponse;
import com.nocteon.nocteon_api.pairing.dto.response.PairingResponseDashboard;
import com.nocteon.nocteon_api.pairing.entity.Pairing;
import com.nocteon.nocteon_api.pairing.entity.PairingTranslation;
import com.nocteon.nocteon_api.pairing.repository.PairingRepository;
import com.nocteon.nocteon_api.pairing.repository.PairingTranslationRepository;
import com.nocteon.nocteon_api.product.enums.MediaType;
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PairingService {

        /** The entity_type every row for this catalogue records. */
        private static final String ENTITY = "Pairing";
        
        private final PairingRepository pairingRepository;
        private final PairingTranslationRepository translationRepository;
        private final LookupServiceHelper helper;
        private final DomainAuditRecorder domainAudit;


        @Transactional
        public PairingResponseDashboard create(PairingRequest request, MultipartFile image) {
                helper.validateTranslations(request.getTranslations());
                helper.assertNoDuplicateNames(
                                request.getTranslations(),
                                PairingTranslationRequest::getLanguage,
                                PairingTranslationRequest::getName,
                                "pairing",
                                null,
                                translationRepository::existsLiveTranslationName);

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(PairingTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                // Serialize slug generation for names that slugify to the same base, and account
                // for soft-deleted rows: pairing slugs are a plain UNIQUE column, so a
                // trashed row still owns its slug even though the duplicate-name guard ignores
                // deleted rows.
                String baseSlug = SlugUtils.toSlug(englishName);
                helper.acquireNameLock("pairing-slug", "slug", baseSlug);
                String slug = helper.generateUniqueSlug(
                                englishName, pairingRepository::existsBySlugIncludingDeleted);
                String imageUrl = helper.uploadMedia(image, "pairings", MediaType.IMAGE);

                Pairing pairing = Pairing.builder()
                                .slug(slug)
                                .imageUrl(imageUrl)
                                .build();

                pairing = pairingRepository.save(pairing);

                final Pairing saved = pairing;
                List<PairingTranslation> translations = new ArrayList<>();
                for (PairingTranslationRequest t : request.getTranslations()) {
                        translations.add(PairingTranslation.builder()
                                        .pairing(saved)
                                        .language(t.getLanguage())
                                        .name(t.getName())
                                        .description(t.getDescription())
                                        .build());
                }
                translationRepository.saveAll(translations);

                var reloaded = pairingRepository.findBySlugWithTranslations(slug).orElse(pairing);


                domainAudit.created(AuditAction.LOOKUP_CREATE, ENTITY, pairing.getId(), slug, lookupFields(reloaded, englishName));

                return buildResponse(reloaded);
        }

        @Transactional
        public PairingResponseDashboard update(String slug, PairingRequest request, MultipartFile image) {
                Pairing pairing = pairingRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(PairingNotFoundException::new);

                Map<String, Object> before = lookupFields(pairing);

                if (image != null && !image.isEmpty()) {
                        helper.deleteMediaIfExists(pairing.getImageUrl());
                        pairing.setImageUrl(helper.uploadMedia(image, "pairings", MediaType.IMAGE));
                }

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
                        helper.assertNoDuplicateNames(
                                        request.getTranslations(),
                                        PairingTranslationRequest::getLanguage,
                                        PairingTranslationRequest::getName,
                                        "pairing",
                                        pairing.getId(),
                                        translationRepository::existsLiveTranslationName);
                        request.getTranslations().forEach(t -> translationRepository
                                        .findByPairingIdAndLanguage(pairing.getId(), t.getLanguage())
                                        .ifPresentOrElse(
                                                        existing -> {
                                                                existing.setName(t.getName());
                                                                existing.setDescription(t.getDescription());
                                                                translationRepository.save(existing);
                                                        },
                                                        () -> translationRepository.save(
                                                                        PairingTranslation.builder()
                                                                                        .pairing(pairing)
                                                                                        .language(t.getLanguage())
                                                                                        .name(t.getName())
                                                                                        .description(t.getDescription())
                                                                                        .build())));
                }

                pairingRepository.save(pairing);
                var reloaded = pairingRepository.findBySlugWithTranslations(pairing.getSlug()).orElse(pairing);

                domainAudit.updated(AuditAction.LOOKUP_UPDATE, ENTITY, pairing.getId(), pairing.getSlug(),
                                before, lookupFields(reloaded));

                return buildResponse(reloaded);
        }

        @Transactional
        public PairingResponseDashboard uploadImage(String slug, MultipartFile file) {
                Pairing pairing = pairingRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(PairingNotFoundException::new);

                helper.deleteMediaIfExists(pairing.getImageUrl());
                pairing.setImageUrl(helper.uploadMedia(file, "pairings", MediaType.IMAGE));
                pairingRepository.save(pairing);

                var reloaded = pairingRepository.findBySlugWithTranslations(slug).orElse(pairing);


                return buildResponse(reloaded);
        }

        @Transactional
        public void delete(String slug) {
                Pairing pairing = pairingRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(PairingNotFoundException::new);
                pairing.softDelete();
                pairingRepository.save(pairing);

                domainAudit.softDeleted(AuditAction.LOOKUP_DELETE, ENTITY, pairing.getId(), slug,
                                pairing.getDeletedAt());
        }

        public PairingResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Pairing pairing = pairingRepository.findBySlugAndLanguage(slug, language)
                                .orElseThrow(PairingNotFoundException::new);
                return buildResponse(pairing, language);
        }

        public PageResponse<PairingResponse> getAll(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Page<Pairing> page = pairingRepository.findAllPublic(
                                SearchPatterns.like(filter.getSearch()),
                                language, filter.toPageable());
                return PageResponse.of(page.map(p -> buildResponse(p, language)));
        }

        @Transactional(readOnly = true)
        public PairingResponseDashboard getDashboardBySlug(String slug) {
                Pairing pairing = pairingRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(PairingNotFoundException::new);
                return buildResponse(pairing);
        }

        @Transactional(readOnly = true)
        public PageResponse<PairingResponseDashboard> getAllDashboard(LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());

                Page<Pairing> page = pairingRepository.findAllDashboard(
                                search,
                                filter.toPageable());
                return PageResponse.of(
                                page.map(this::buildResponse));
        }

        /**
         * Create-time snapshot.
         *
         * <p>The translations are written through their own repository, so the
         * entity's collection is still empty inside this transaction and a read of
         * it would record a null name. The English name the admin submitted is the
         * label the row records.
         */
        private static Map<String, Object> lookupFields(Pairing entity, String englishName) {
                Map<String, Object> fields = lookupFields(entity);
                fields.put("name", englishName);
                return fields;
        }

        /**
         * The change-visible state of one pairing, for an audit row: what the
         * dashboard shows an editor, and nothing only a DB admin reads.
         */
        private static Map<String, Object> lookupFields(Pairing pairing) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("slug", pairing.getSlug());
                fields.put("imageUrl", pairing.getImageUrl());
                String name = pairing.getTranslations() == null ? null : pairing.getTranslations().stream()
                                .filter(t -> "en".equals(t.getLanguage()))
                                .map(PairingTranslation::getName)
                                .findFirst().orElse(null);
                fields.put("name", name);
                return fields;
        }

        private PairingResponse buildResponse(Pairing pairing, String language) {
                List<PairingTranslation> translations = translationRepository
                                .findByPairingId(pairing.getId());

                PairingTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                return PairingResponse.builder()
                                .id(pairing.getId())
                                .slug(pairing.getSlug())
                                .name(translation != null ? translation.getName() : null)
                                .description(translation != null ? translation.getDescription() : null)
                                .imageUrl(pairing.getImageUrl())
                                .createdAt(pairing.getCreatedAt())
                                .build();
        }

        private PairingResponseDashboard buildResponse(Pairing pairing) {
                return PairingResponseDashboard.builder()
                                .id(pairing.getId())
                                .slug(pairing.getSlug())
                                .createdAt(pairing.getCreatedAt())
                                .imageUrl(pairing.getImageUrl())
                                .translations(
                                                pairing.getTranslations()
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
         * Trashed Pairings for the dashboard's deleted-items table.
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
        public PageResponse<PairingResponseDashboard> getAllDeleted(LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());
                int pageNum = filter.getPage();
                int size = filter.getSize();
                String sort = filter.getSort();
                String direction = filter.getDirection();

                List<Object[]> rawResults = pairingRepository.findDeletedBySearchNative(search);

                List<PairingResponseDashboard> allResults = SoftDeleteRowFolder.fold(
                                rawResults,
                                row -> ((Number) row[0]).longValue(),
                                this::mapToPairingResponseDashboard,
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
                List<PairingResponseDashboard> pagedResults = allResults.subList(fromIndex, toIndex);

                Page<PairingResponseDashboard> page = new org.springframework.data.domain.PageImpl<>(
                                pagedResults,
                                org.springframework.data.domain.PageRequest.of(pageNum, size),
                                allResults.size());
                return PageResponse.of(page);
        }

        private PairingResponseDashboard mapToPairingResponseDashboard(Object[] row) {
                return PairingResponseDashboard.builder()
                                .id(((Number) row[0]).longValue())
                                .slug((String) row[1])
                                .imageUrl((String) row[2])
                                .createdAt((Instant) row[3])
                                .translations(new ArrayList<>())
                                .build();
        }

        private TranslationResponse buildTranslation(Object[] row) {
                return TranslationResponse.builder()
                                .language((String) row[4])
                                .name((String) row[5])
                                .description((String) row[6])

                                .build();
        }

        /**
         * Un-delete a Pairing.
         *
         * <p>Reads through {@code findDeletedById}, so an id that is live or unknown raises
         * {@link PairingNotFoundException} rather than reporting a restore that changed nothing.
         */
        @Transactional
        public void restore(Long id) {
                Pairing pairing = pairingRepository.findDeletedById(id)
                                .orElseThrow(PairingNotFoundException::new);
                helper.assertNoDuplicateNames(
                                translationRepository.findByPairingId(pairing.getId()),
                                PairingTranslation::getLanguage,
                                PairingTranslation::getName,
                                "pairing",
                                pairing.getId(),
                                translationRepository::existsLiveTranslationName);
                pairing.setDeletedAt(null);
                pairingRepository.save(pairing);
                log.info("Pairing restored: {}", pairing.getSlug());
        }

        /**
         * Permanently deletes a trashed Pairing: removes its image from Cloudinary, then the row itself.
         *
         * <p>Deletes the row — and flushes, so a foreign-key conflict surfaces here — <em>before</em>
         * touching Cloudinary. If the database refuses the delete, the trashed row and its image
         * are both untouched and the admin can retry; the reverse order would strand a restored
         * item without its picture.
         *
         * <p>Trash-only: reads through {@code findDeletedById}, so a live or unknown id raises
         * {@link PairingNotFoundException} instead of deleting anything. Soft-delete first — this never touches live
         * rows.
         */
        @Transactional
        public void permanentDelete(Long id) {
                Pairing pairing = pairingRepository.findDeletedById(id)
                                .orElseThrow(PairingNotFoundException::new);
                pairingRepository.delete(pairing);
                pairingRepository.flush();
                String imageUrl = pairing.getImageUrl();
                String slug = pairing.getSlug();
                AfterCommit.run(() -> {
                    try {
                        helper.deleteMediaIfExists(imageUrl);
                    } catch (Exception e) {
                        log.warn("Cloudinary delete failed for pairing {} ({}): {}", slug, imageUrl, e.getMessage());
                    }
                });
                log.info("Pairing permanently deleted: {}", slug);
        }
}
