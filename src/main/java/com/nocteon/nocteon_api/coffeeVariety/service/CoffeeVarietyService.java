package com.nocteon.nocteon_api.coffeeVariety.service;

import java.time.Instant;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.coffeeVariety.dto.request.CoffeeVarietyRequest;
import com.nocteon.nocteon_api.coffeeVariety.dto.request.CoffeeVarietyTranslationRequest;
import com.nocteon.nocteon_api.coffeeVariety.dto.response.CoffeeVarietyResponse;
import com.nocteon.nocteon_api.coffeeVariety.dto.response.CoffeeVarietyResponseDashboard;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVariety;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVarietyTranslation;
import com.nocteon.nocteon_api.coffeeVariety.repository.CoffeeVarietyRepository;
import com.nocteon.nocteon_api.product.repository.CoffeeDetailsRepository;
import com.nocteon.nocteon_api.coffeeVariety.repository.CoffeeVarietyTranslationRepository;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.dto.TranslationResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.invalid.EntityInUseException;
import com.nocteon.nocteon_api.common.exception.notFound.CoffeeVarietyNotFoundException;
import com.nocteon.nocteon_api.common.service.SoftDeleteRowFolder;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.common.util.SlugUtils;
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoffeeVarietyService {

        private final CoffeeVarietyRepository coffeeVarietyRepository;
        private final CoffeeVarietyTranslationRepository translationRepository;
        private final LookupServiceHelper helper;
        private final CoffeeDetailsRepository coffeeDetailsRepository;

        @Transactional
        public CoffeeVarietyResponseDashboard create(CoffeeVarietyRequest request) {
                helper.validateTranslations(request.getTranslations());
                helper.assertNoDuplicateNames(
                                request.getTranslations(),
                                CoffeeVarietyTranslationRequest::getLanguage,
                                CoffeeVarietyTranslationRequest::getName,
                                "coffee-variety",
                                null,
                                translationRepository::existsLiveTranslationName);

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(CoffeeVarietyTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                // Serialize slug generation for names that slugify to the same base, and account
                // for soft-deleted rows: coffee-variety slugs are a plain UNIQUE column, so a
                // trashed row still owns its slug even though the duplicate-name guard ignores
                // deleted rows.
                String baseSlug = SlugUtils.toSlug(englishName);
                helper.acquireNameLock("coffee-variety-slug", "slug", baseSlug);
                String slug = helper.generateUniqueSlug(
                                englishName, coffeeVarietyRepository::existsBySlugIncludingDeleted);

                CoffeeVariety coffeeVariety = CoffeeVariety.builder()
                                .slug(slug)
                                .build();

                coffeeVariety = coffeeVarietyRepository.save(coffeeVariety);

                final CoffeeVariety saved = coffeeVariety;
                List<CoffeeVarietyTranslation> translations = new ArrayList<>();
                for (CoffeeVarietyTranslationRequest t : request.getTranslations()) {
                        translations.add(CoffeeVarietyTranslation.builder()
                                        .coffeeVariety(saved)
                                        .language(t.getLanguage())
                                        .name(t.getName())
                                        .description(t.getDescription())
                                        .build());
                }
                translationRepository.saveAll(translations);

                var reloaded = coffeeVarietyRepository.findBySlugWithTranslations(slug).orElse(coffeeVariety);


                return buildResponse(reloaded);
        }

        @Transactional
        public CoffeeVarietyResponseDashboard update(String slug, CoffeeVarietyRequest request) {
                CoffeeVariety coffeeVariety = coffeeVarietyRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(CoffeeVarietyNotFoundException::new);

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
                        helper.assertNoDuplicateNames(
                                        request.getTranslations(),
                                        CoffeeVarietyTranslationRequest::getLanguage,
                                        CoffeeVarietyTranslationRequest::getName,
                                        "coffee-variety",
                                        coffeeVariety.getId(),
                                        translationRepository::existsLiveTranslationName);
                        request.getTranslations().forEach(t -> translationRepository
                                        .findByCoffeeVarietyIdAndLanguage(coffeeVariety.getId(), t.getLanguage())
                                        .ifPresentOrElse(
                                                        existing -> {
                                                                existing.setName(t.getName());
                                                                existing.setDescription(t.getDescription());
                                                                translationRepository.save(existing);
                                                        },
                                                        () -> translationRepository.save(
                                                                        CoffeeVarietyTranslation.builder()
                                                                                        .coffeeVariety(coffeeVariety)
                                                                                        .language(t.getLanguage())
                                                                                        .name(t.getName())
                                                                                        .description(t.getDescription())
                                                                                        .build())));
                }

                coffeeVarietyRepository.save(coffeeVariety);
                var reloaded = coffeeVarietyRepository.findBySlugWithTranslations(coffeeVariety.getSlug()).orElse(coffeeVariety);

                return buildResponse(reloaded);
        }

        @Transactional
        public void delete(String slug) {
                CoffeeVariety coffeeVariety = coffeeVarietyRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(CoffeeVarietyNotFoundException::new);

                if (coffeeDetailsRepository.countByCoffeeVarietyId(coffeeVariety.getId()) > 0) {
                        throw new EntityInUseException();
                }

                coffeeVariety.softDelete();
                coffeeVarietyRepository.save(coffeeVariety);
        }

        public CoffeeVarietyResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                CoffeeVariety coffeeVariety = coffeeVarietyRepository.findBySlugAndLanguage(slug, language)
                                .orElseThrow(CoffeeVarietyNotFoundException::new);
                return buildResponse(coffeeVariety, language);
        }

        public PageResponse<CoffeeVarietyResponse> getAll(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Page<CoffeeVariety> page = coffeeVarietyRepository.findAllPublic(
                                SearchPatterns.like(filter.getSearch()),
                                language, filter.toPageable());
                return PageResponse.of(page.map(c -> buildResponse(c, language)));
        }

        @Transactional(readOnly = true)
        public CoffeeVarietyResponseDashboard getDashboardBySlug(String slug) {
                CoffeeVariety coffeeVariety = coffeeVarietyRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(CoffeeVarietyNotFoundException::new);
                return buildResponse(coffeeVariety);
        }

        @Transactional(readOnly = true)
        public PageResponse<CoffeeVarietyResponseDashboard> getAllDashboard(LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());

                
                Page<CoffeeVariety> page = coffeeVarietyRepository.findAllDashboard(
                                search,
                                filter.toPageable());

                return PageResponse.of(
                                page.map(this::buildResponse));
        }

        private CoffeeVarietyResponse buildResponse(CoffeeVariety coffeeVariety, String language) {
                List<CoffeeVarietyTranslation> translations = translationRepository
                                .findByCoffeeVarietyId(coffeeVariety.getId());

                CoffeeVarietyTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                return CoffeeVarietyResponse.builder()
                                .id(coffeeVariety.getId())
                                .slug(coffeeVariety.getSlug())
                                .name(translation != null ? translation.getName() : null)
                                .description(translation != null ? translation.getDescription() : null)
                                .createdAt(coffeeVariety.getCreatedAt())
                                .build();
        }

        
                private CoffeeVarietyResponseDashboard buildResponse(CoffeeVariety coffeeVariety) {
                return CoffeeVarietyResponseDashboard.builder()
                                .id(coffeeVariety.getId())
                                .slug(coffeeVariety.getSlug())
                                .createdAt(coffeeVariety.getCreatedAt())
                                .translations(
                                                coffeeVariety.getTranslations()
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
         * Trashed CoffeeVarietys for the dashboard's deleted-items table.
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
        public PageResponse<CoffeeVarietyResponseDashboard> getAllDeleted(LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());
                int pageNum = filter.getPage();
                int size = filter.getSize();
                String sort = filter.getSort();
                String direction = filter.getDirection();

                List<Object[]> rawResults = coffeeVarietyRepository.findDeletedBySearchNative(search);

                List<CoffeeVarietyResponseDashboard> allResults = SoftDeleteRowFolder.fold(
                                rawResults,
                                row -> ((Number) row[0]).longValue(),
                                this::mapToCoffeeVarietyResponseDashboard,
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
                List<CoffeeVarietyResponseDashboard> pagedResults = allResults.subList(fromIndex, toIndex);

                Page<CoffeeVarietyResponseDashboard> page = new org.springframework.data.domain.PageImpl<>(
                                pagedResults,
                                org.springframework.data.domain.PageRequest.of(pageNum, size),
                                allResults.size());
                return PageResponse.of(page);
        }

        private CoffeeVarietyResponseDashboard mapToCoffeeVarietyResponseDashboard(Object[] row) {
                return CoffeeVarietyResponseDashboard.builder()
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
         * Un-delete a CoffeeVariety.
         *
         * <p>Reads through {@code findDeletedById}, so an id that is live or unknown raises
         * {@link CoffeeVarietyNotFoundException} rather than reporting a restore that changed nothing.
         */
        @Transactional
        public void restore(Long id) {
                CoffeeVariety coffeeVariety = coffeeVarietyRepository.findDeletedById(id)
                                .orElseThrow(CoffeeVarietyNotFoundException::new);
                helper.assertNoDuplicateNames(
                                translationRepository.findByCoffeeVarietyId(coffeeVariety.getId()),
                                CoffeeVarietyTranslation::getLanguage,
                                CoffeeVarietyTranslation::getName,
                                "coffee-variety",
                                coffeeVariety.getId(),
                                translationRepository::existsLiveTranslationName);
                coffeeVariety.setDeletedAt(null);
                coffeeVarietyRepository.save(coffeeVariety);
                log.info("CoffeeVariety restored: {}", coffeeVariety.getSlug());
        }

        /**
         * Permanently deletes a trashed CoffeeVariety: removes the row itself (it carries no media, so there is nothing to clean on Cloudinary).
         *
         * <p>Trash-only: reads through {@code findDeletedById}, so a live or unknown id raises
         * {@link CoffeeVarietyNotFoundException} instead of deleting anything. Soft-delete first — this never touches live
         * rows.
         */
        @Transactional
        public void permanentDelete(Long id) {
                CoffeeVariety coffeeVariety = coffeeVarietyRepository.findDeletedById(id)
                                .orElseThrow(CoffeeVarietyNotFoundException::new);
                coffeeVarietyRepository.delete(coffeeVariety);
                log.info("CoffeeVariety permanently deleted: {}", coffeeVariety.getSlug());
        }
}
