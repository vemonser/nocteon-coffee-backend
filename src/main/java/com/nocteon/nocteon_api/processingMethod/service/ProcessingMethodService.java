package com.nocteon.nocteon_api.processingMethod.service;

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
import com.nocteon.nocteon_api.common.exception.notFound.ProcessingMethodNotFoundException;
import com.nocteon.nocteon_api.common.service.SoftDeleteRowFolder;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.common.util.SlugUtils;
import com.nocteon.nocteon_api.processingMethod.dto.request.ProcessingMethodRequest;
import com.nocteon.nocteon_api.processingMethod.dto.request.ProcessingMethodTranslationRequest;
import com.nocteon.nocteon_api.processingMethod.dto.response.ProcessingMethodResponse;
import com.nocteon.nocteon_api.processingMethod.dto.response.ProcessingMethodResponseDashboard;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethodTranslation;
import com.nocteon.nocteon_api.processingMethod.repository.ProcessingMethodRepository;
import com.nocteon.nocteon_api.product.repository.CoffeeDetailsRepository;
import com.nocteon.nocteon_api.processingMethod.repository.ProcessingMethodTranslationRepository;
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingMethodService {

        /** The entity_type every row for this catalogue records. */
        private static final String ENTITY = "ProcessingMethod";

        private final ProcessingMethodRepository processingMethodRepository;
        private final ProcessingMethodTranslationRepository translationRepository;
        private final LookupServiceHelper helper;
        private final DomainAuditRecorder domainAudit;
        private final CoffeeDetailsRepository coffeeDetailsRepository;

        public PageResponse<ProcessingMethodResponse> getAll(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Page<ProcessingMethod> page = processingMethodRepository.findAllPublic(
                                SearchPatterns.like(filter.getSearch()),
                                language, filter.toPageable());
                return PageResponse.of(page.map(p -> buildResponse(p, language)));
        }

        @Transactional(readOnly = true)
        public PageResponse<ProcessingMethodResponseDashboard> getAllDashboard(LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());

                Page<ProcessingMethod> page = processingMethodRepository.findAllDashboard(
                                search, filter.toPageable());

                return PageResponse.of(
                                page.map(this::buildResponse));
        }

        public ProcessingMethodResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                ProcessingMethod processingMethod = processingMethodRepository
                                .findBySlugAndLanguage(slug, language)
                                .orElseThrow(ProcessingMethodNotFoundException::new);
                return buildResponse(processingMethod, language);
        }

        @Transactional(readOnly = true)
        public ProcessingMethodResponseDashboard getDashboardBySlug(String slug) {
                ProcessingMethod processingMethod = processingMethodRepository
                                .findBySlugWithTranslations(slug)
                                .orElseThrow(ProcessingMethodNotFoundException::new);
                return buildResponse(processingMethod);
        }

        @Transactional
        public ProcessingMethodResponseDashboard create(ProcessingMethodRequest request) {
                helper.validateTranslations(request.getTranslations());
                helper.assertNoDuplicateNames(
                                request.getTranslations(),
                                ProcessingMethodTranslationRequest::getLanguage,
                                ProcessingMethodTranslationRequest::getName,
                                "processing-method",
                                null,
                                translationRepository::existsLiveTranslationName);

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(ProcessingMethodTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                // Serialize slug generation for names that slugify to the same base, and account
                // for soft-deleted rows: processing-method slugs are a plain UNIQUE column, so a
                // trashed row still owns its slug even though the duplicate-name guard ignores
                // deleted rows.
                String baseSlug = SlugUtils.toSlug(englishName);
                helper.acquireNameLock("processing-method-slug", "slug", baseSlug);
                String slug = helper.generateUniqueSlug(
                                englishName, processingMethodRepository::existsBySlugIncludingDeleted);

                ProcessingMethod processingMethod = ProcessingMethod.builder()
                                .slug(slug)
                                .build();

                processingMethod = processingMethodRepository.save(processingMethod);

                final ProcessingMethod saved = processingMethod;
                List<ProcessingMethodTranslation> translations = new ArrayList<>();
                for (ProcessingMethodTranslationRequest t : request.getTranslations()) {
                        translations.add(ProcessingMethodTranslation.builder()
                                        .processingMethod(saved)
                                        .language(t.getLanguage())
                                        .name(t.getName())
                                        .description(t.getDescription())
                                        .build());
                }
                translationRepository.saveAll(translations);

                log.info("ProcessingMethod created with slug: {}", slug);
                var reloaded = processingMethodRepository.findBySlugWithTranslations(slug).orElse(processingMethod);
                domainAudit.created(AuditAction.LOOKUP_CREATE, ENTITY, processingMethod.getId(), slug, lookupFields(reloaded, englishName));

                return buildResponse(reloaded);
        }

        @Transactional
        public ProcessingMethodResponseDashboard update(String slug, ProcessingMethodRequest request) {
                ProcessingMethod processingMethod = processingMethodRepository
                                .findBySlugWithTranslations(slug)
                                .orElseThrow(ProcessingMethodNotFoundException::new);

                Map<String, Object> before = lookupFields(processingMethod);

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
                        helper.assertNoDuplicateNames(
                                        request.getTranslations(),
                                        ProcessingMethodTranslationRequest::getLanguage,
                                        ProcessingMethodTranslationRequest::getName,
                                        "processing-method",
                                        processingMethod.getId(),
                                        translationRepository::existsLiveTranslationName);
                        request.getTranslations().forEach(t -> translationRepository
                                        .findByProcessingMethodIdAndLanguage(processingMethod.getId(), t.getLanguage())
                                        .ifPresentOrElse(
                                                        existing -> {
                                                                existing.setName(t.getName());
                                                                existing.setDescription(t.getDescription());
                                                                translationRepository.save(existing);
                                                        },
                                                        () -> translationRepository.save(
                                                                        ProcessingMethodTranslation.builder()
                                                                                        .processingMethod(
                                                                                                        processingMethod)
                                                                                        .language(t.getLanguage())
                                                                                        .name(t.getName())
                                                                                        .description(t.getDescription())
                                                                                        .build())));
                }

                processingMethodRepository.save(processingMethod);
                var reloaded = processingMethodRepository.findBySlugWithTranslations(processingMethod.getSlug()).orElse(processingMethod);

                domainAudit.updated(AuditAction.LOOKUP_UPDATE, ENTITY, processingMethod.getId(), processingMethod.getSlug(),
                                before, lookupFields(reloaded));

                return buildResponse(reloaded);
        }

        @Transactional
        public void delete(String slug) {
                ProcessingMethod processingMethod = processingMethodRepository
                                .findBySlugWithTranslations(slug)
                                .orElseThrow(ProcessingMethodNotFoundException::new);

                if (coffeeDetailsRepository.countByProcessingMethodId(processingMethod.getId()) > 0) {
                        throw new EntityInUseException();
                }

                processingMethod.softDelete();
                processingMethodRepository.save(processingMethod);

                domainAudit.softDeleted(AuditAction.LOOKUP_DELETE, ENTITY, processingMethod.getId(), slug,
                                processingMethod.getDeletedAt());
                log.info("ProcessingMethod soft deleted: {}", slug);
        }

        /**
         * Create-time snapshot.
         *
         * <p>The translations are written through their own repository, so the
         * entity's collection is still empty inside this transaction and a read of
         * it would record a null name. The English name the admin submitted is the
         * label the row records.
         */
        private static Map<String, Object> lookupFields(ProcessingMethod entity, String englishName) {
                Map<String, Object> fields = lookupFields(entity);
                fields.put("name", englishName);
                return fields;
        }

        /**
         * The change-visible state of one processing method, for an audit row: what the
         * dashboard shows an editor, and nothing only a DB admin reads.
         */
        private static Map<String, Object> lookupFields(ProcessingMethod processingMethod) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("slug", processingMethod.getSlug());
                String name = processingMethod.getTranslations() == null ? null : processingMethod.getTranslations().stream()
                                .filter(t -> "en".equals(t.getLanguage()))
                                .map(ProcessingMethodTranslation::getName)
                                .findFirst().orElse(null);
                fields.put("name", name);
                return fields;
        }

        private ProcessingMethodResponse buildResponse(ProcessingMethod processingMethod, String language) {
                List<ProcessingMethodTranslation> translations = translationRepository
                                .findByProcessingMethodId(processingMethod.getId());

                ProcessingMethodTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                return ProcessingMethodResponse.builder()
                                .id(processingMethod.getId())
                                .slug(processingMethod.getSlug())
                                .name(translation != null ? translation.getName() : null)
                                .description(translation != null ? translation.getDescription() : null)
                                .build();
        }

        private ProcessingMethodResponseDashboard buildResponse(ProcessingMethod processingMethod) {
                return ProcessingMethodResponseDashboard.builder()
                                .id(processingMethod.getId())
                                .slug(processingMethod.getSlug())
                                .createdAt(processingMethod.getCreatedAt())
                                .translations(
                                                processingMethod.getTranslations()
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
         * Trashed ProcessingMethods for the dashboard's deleted-items table.
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
        public PageResponse<ProcessingMethodResponseDashboard> getAllDeleted(LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());
                int pageNum = filter.getPage();
                int size = filter.getSize();
                String sort = filter.getSort();
                String direction = filter.getDirection();

                List<Object[]> rawResults = processingMethodRepository.findDeletedBySearchNative(search);

                List<ProcessingMethodResponseDashboard> allResults = SoftDeleteRowFolder.fold(
                                rawResults,
                                row -> ((Number) row[0]).longValue(),
                                this::mapToProcessingMethodResponseDashboard,
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
                List<ProcessingMethodResponseDashboard> pagedResults = allResults.subList(fromIndex, toIndex);

                Page<ProcessingMethodResponseDashboard> page = new org.springframework.data.domain.PageImpl<>(
                                pagedResults,
                                org.springframework.data.domain.PageRequest.of(pageNum, size),
                                allResults.size());
                return PageResponse.of(page);
        }

        private ProcessingMethodResponseDashboard mapToProcessingMethodResponseDashboard(Object[] row) {
                return ProcessingMethodResponseDashboard.builder()
                                .id(((Number) row[0]).longValue())
                                .slug((String) row[1])
                                .createdAt((Instant) row[2])
                                .translations(new ArrayList<>())
                                .build();
        }

        private TranslationResponse buildTranslation(Object[] row) {
                return TranslationResponse.builder()
                                .language((String) row[3])
                                .name((String) row[4])
                                .description((String) row[5])

                                .build();
        }

        /**
         * Un-delete a ProcessingMethod.
         *
         * <p>Reads through {@code findDeletedById}, so an id that is live or unknown raises
         * {@link ProcessingMethodNotFoundException} rather than reporting a restore that changed nothing.
         */
        @Transactional
        public void restore(Long id) {
                ProcessingMethod processingMethod = processingMethodRepository.findDeletedById(id)
                                .orElseThrow(ProcessingMethodNotFoundException::new);
                helper.assertNoDuplicateNames(
                                translationRepository.findByProcessingMethodId(processingMethod.getId()),
                                ProcessingMethodTranslation::getLanguage,
                                ProcessingMethodTranslation::getName,
                                "processing-method",
                                processingMethod.getId(),
                                translationRepository::existsLiveTranslationName);
                processingMethod.setDeletedAt(null);
                processingMethodRepository.save(processingMethod);
                log.info("ProcessingMethod restored: {}", processingMethod.getSlug());
        }

        /**
         * Permanently deletes a trashed ProcessingMethod: removes the row itself (it carries no media, so there is nothing to clean on Cloudinary).
         *
         * <p>Trash-only: reads through {@code findDeletedById}, so a live or unknown id raises
         * {@link ProcessingMethodNotFoundException} instead of deleting anything. Soft-delete first — this never touches live
         * rows.
         */
        @Transactional
        public void permanentDelete(Long id) {
                ProcessingMethod processingMethod = processingMethodRepository.findDeletedById(id)
                                .orElseThrow(ProcessingMethodNotFoundException::new);
                processingMethodRepository.delete(processingMethod);
                log.info("ProcessingMethod permanently deleted: {}", processingMethod.getSlug());
        }
}
