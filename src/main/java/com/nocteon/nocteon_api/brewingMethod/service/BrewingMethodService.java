package com.nocteon.nocteon_api.brewingMethod.service;

import java.time.Instant;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.brewingMethod.dto.request.BrewingMethodRequest;
import com.nocteon.nocteon_api.brewingMethod.dto.request.BrewingMethodTranslationRequest;
import com.nocteon.nocteon_api.brewingMethod.dto.response.BrewingMethodResponse;
import com.nocteon.nocteon_api.brewingMethod.dto.response.BrewingMethodResponseDashboard;
import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;
import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethodTranslation;
import com.nocteon.nocteon_api.brewingMethod.repository.BrewingMethodRepository;
import com.nocteon.nocteon_api.brewingMethod.repository.BrewingMethodTranslationRepository;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.dto.TranslationResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.BrewingMethodNotFoundException;
import com.nocteon.nocteon_api.common.service.SoftDeleteRowFolder;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.product.dto.response.ProductWithScoreResponse;
import com.nocteon.nocteon_api.product.entity.ProductBrewingMethod;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductBrewingMethodRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrewingMethodService {

        private final BrewingMethodRepository brewingMethodRepository;
        private final BrewingMethodTranslationRepository translationRepository;
        private final LookupServiceHelper helper;
        private final ProductBrewingMethodRepository productBrewingMethodRepository;
        private final ProductResponseMapper productResponseMapper;

        private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("score", "createdAt");

        public PageResponse<ProductWithScoreResponse> getProductsByBrewingMethod(
                        String slug, int page, int size, String sort, String direction) {
                if (!brewingMethodRepository.existsBySlug(slug)) {
                        throw new BrewingMethodNotFoundException();
                }

                String language = LocaleContextHolder.getLocale().getLanguage();
                Pageable pageable = PageRequest.of(page, size, resolveSort(sort, direction));

                Page<ProductBrewingMethod> pbmPage = productBrewingMethodRepository
                                .findByBrewingMethodSlug(slug, pageable);

                return PageResponse.of(
                                pbmPage.map(pbm -> productResponseMapper.buildProductWithScoreResponse(pbm, language)));
        }

        private Sort resolveSort(String sort, String direction) {
                String field = (sort != null && ALLOWED_SORT_FIELDS.contains(sort)) ? sort : "score";
                Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
                return Sort.by(dir, field);
        }

        public BrewingMethodResponseDashboard getDashboardBySlug(String slug) {
                BrewingMethod brewingMethod = brewingMethodRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(BrewingMethodNotFoundException::new);
                return buildResponse(brewingMethod);
        }

        @Transactional
        public BrewingMethodResponseDashboard create(BrewingMethodRequest request) {
                helper.validateTranslations(request.getTranslations());

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(BrewingMethodTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                String slug = helper.generateUniqueSlug(englishName, brewingMethodRepository::existsBySlug);

                BrewingMethod brewingMethod = BrewingMethod.builder()
                                .slug(slug)
                                .build();

                brewingMethod = brewingMethodRepository.save(brewingMethod);

                final BrewingMethod saved = brewingMethod;
                List<BrewingMethodTranslation> translations = new ArrayList<>();
                for (BrewingMethodTranslationRequest t : request.getTranslations()) {
                        translations.add(BrewingMethodTranslation.builder()
                                        .brewingMethod(saved)
                                        .language(t.getLanguage())
                                        .name(t.getName())
                                        .description(t.getDescription())
                                        .build());
                }
                translationRepository.saveAll(translations);

                var reloaded = brewingMethodRepository.findBySlugWithTranslations(slug).orElse(brewingMethod);


                return buildResponse(reloaded);
        }

        @Transactional
        public BrewingMethodResponseDashboard update(String slug, BrewingMethodRequest request) {
                BrewingMethod brewingMethod = brewingMethodRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(BrewingMethodNotFoundException::new);

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
                        request.getTranslations().forEach(t -> translationRepository
                                        .findByBrewingMethodIdAndLanguage(brewingMethod.getId(), t.getLanguage())
                                        .ifPresentOrElse(
                                                        existing -> {
                                                                existing.setName(t.getName());
                                                                existing.setDescription(t.getDescription());
                                                                translationRepository.save(existing);
                                                        },
                                                        () -> translationRepository.save(
                                                                        BrewingMethodTranslation.builder()
                                                                                        .brewingMethod(brewingMethod)
                                                                                        .language(t.getLanguage())
                                                                                        .name(t.getName())
                                                                                        .description(t.getDescription())
                                                                                        .build())));
                }

                brewingMethodRepository.save(brewingMethod);
                var reloaded = brewingMethodRepository.findBySlugWithTranslations(brewingMethod.getSlug()).orElse(brewingMethod);

                return buildResponse(reloaded);
        }

        @Transactional
        public void delete(String slug) {
                BrewingMethod brewingMethod = brewingMethodRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(BrewingMethodNotFoundException::new);
                brewingMethod.softDelete();
                brewingMethodRepository.save(brewingMethod);
        }

        public BrewingMethodResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                BrewingMethod brewingMethod = brewingMethodRepository.findBySlugAndLanguage(slug, language)
                                .orElseThrow(BrewingMethodNotFoundException::new);
                return buildResponse(brewingMethod, language);
        }

        public PageResponse<BrewingMethodResponse> getAll(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Page<BrewingMethod> page = brewingMethodRepository.findAllPublic(
                                filter.getSearch(),
                                language, filter.toPageable());
                return PageResponse.of(page.map(b -> buildResponse(b, language)));
        }

        public PageResponse<BrewingMethodResponseDashboard> getAllDashboard(LookupFilterRequest filter) {

                String search = Objects.requireNonNullElse(
                                filter.getSearch(),
                                "");

                Page<BrewingMethod> page = brewingMethodRepository.findAllDashboard(
                                search,
                                filter.toPageable());

                return PageResponse.of(
                                page.map(this::buildResponse));
        }

        private BrewingMethodResponse buildResponse(BrewingMethod brewingMethod, String language) {
                List<BrewingMethodTranslation> translations = translationRepository
                                .findByBrewingMethodId(brewingMethod.getId());

                BrewingMethodTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                return BrewingMethodResponse.builder()
                                .id(brewingMethod.getId())
                                .slug(brewingMethod.getSlug())
                                .createdAt(brewingMethod.getCreatedAt())
                                .name(translation != null ? translation.getName() : null)
                                .description(translation != null ? translation.getDescription() : null)
                                .build();
        }

        private BrewingMethodResponseDashboard buildResponse(BrewingMethod brewingMethod) {
                return BrewingMethodResponseDashboard.builder()
                                .id(brewingMethod.getId())
                                .slug(brewingMethod.getSlug())
                                .createdAt(brewingMethod.getCreatedAt())
                                .translations(
                                                brewingMethod.getTranslations()
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
         * Trashed BrewingMethods for the dashboard's deleted-items table.
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
        public PageResponse<BrewingMethodResponseDashboard> getAllDeleted(LookupFilterRequest filter) {
                String search = Objects.requireNonNullElse(filter.getSearch(), "");
                int pageNum = filter.getPage();
                int size = filter.getSize();
                String sort = filter.getSort();
                String direction = filter.getDirection();

                List<Object[]> rawResults = brewingMethodRepository.findDeletedBySearchNative(search);

                List<BrewingMethodResponseDashboard> allResults = SoftDeleteRowFolder.fold(
                                rawResults,
                                row -> ((Number) row[0]).longValue(),
                                this::mapToBrewingMethodResponseDashboard,
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
                List<BrewingMethodResponseDashboard> pagedResults = allResults.subList(fromIndex, toIndex);

                Page<BrewingMethodResponseDashboard> page = new org.springframework.data.domain.PageImpl<>(
                                pagedResults,
                                org.springframework.data.domain.PageRequest.of(pageNum, size),
                                allResults.size());
                return PageResponse.of(page);
        }

        private BrewingMethodResponseDashboard mapToBrewingMethodResponseDashboard(Object[] row) {
                return BrewingMethodResponseDashboard.builder()
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
         * Un-delete a BrewingMethod.
         *
         * <p>Reads through {@code findDeletedById}, so an id that is live or unknown raises
         * {@link BrewingMethodNotFoundException} rather than reporting a restore that changed nothing.
         */
        @Transactional
        public void restore(Long id) {
                BrewingMethod brewingMethod = brewingMethodRepository.findDeletedById(id)
                                .orElseThrow(BrewingMethodNotFoundException::new);
                brewingMethod.setDeletedAt(null);
                brewingMethodRepository.save(brewingMethod);
                log.info("BrewingMethod restored: {}", brewingMethod.getSlug());
        }

        /**
         * Permanently deletes a trashed BrewingMethod: removes the row itself (it carries no media, so there is nothing to clean on Cloudinary).
         *
         * <p>Trash-only: reads through {@code findDeletedById}, so a live or unknown id raises
         * {@link BrewingMethodNotFoundException} instead of deleting anything. Soft-delete first — this never touches live
         * rows.
         */
        @Transactional
        public void permanentDelete(Long id) {
                BrewingMethod brewingMethod = brewingMethodRepository.findDeletedById(id)
                                .orElseThrow(BrewingMethodNotFoundException::new);
                brewingMethodRepository.delete(brewingMethod);
                log.info("BrewingMethod permanently deleted: {}", brewingMethod.getSlug());
        }
}
