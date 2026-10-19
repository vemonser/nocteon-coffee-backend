package com.nocteon.nocteon_api.roastLevel.service;

import java.time.Instant;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.dto.TranslationResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.invalid.EntityInUseException;
import com.nocteon.nocteon_api.common.exception.notFound.RoastLevelNotFoundException;
import com.nocteon.nocteon_api.common.service.SoftDeleteRowFolder;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.roastLevel.dto.request.RoastLevelRequest;
import com.nocteon.nocteon_api.roastLevel.dto.request.RoastLevelTranslationRequest;
import com.nocteon.nocteon_api.roastLevel.dto.response.DashboardRoastLevelResponse;
import com.nocteon.nocteon_api.roastLevel.dto.response.RoastLevelResponse;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevelTranslation;
import com.nocteon.nocteon_api.roastLevel.repository.RoastLevelRepository;
import com.nocteon.nocteon_api.product.repository.CoffeeDetailsRepository;
import com.nocteon.nocteon_api.roastLevel.repository.RoastLevelTranslationRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoastLevelService {

        private final RoastLevelRepository roastLevelRepository;
        private final RoastLevelTranslationRepository translationRepository;
        private final LookupServiceHelper helper;
        private final CoffeeDetailsRepository coffeeDetailsRepository;

        @Transactional
        public DashboardRoastLevelResponse create(RoastLevelRequest request) {
                helper.validateTranslations(request.getTranslations());

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(RoastLevelTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                String slug = helper.generateUniqueSlug(englishName, roastLevelRepository::existsBySlug);

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


                return buildResponse(reloaded);
        }

        @Transactional
        public DashboardRoastLevelResponse update(String slug, RoastLevelRequest request) {
                RoastLevel roastLevel = roastLevelRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(RoastLevelNotFoundException::new);
                
                roastLevel.setColor(request.getColor());

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
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

                return buildResponse(reloaded);
        }

        @Transactional
        public void delete(String slug) {
                RoastLevel roastLevel = roastLevelRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(RoastLevelNotFoundException::new);

                if (coffeeDetailsRepository.countByRoastLevelId(roastLevel.getId()) > 0) {
                        throw new EntityInUseException();
                }

                roastLevel.softDelete();
                roastLevelRepository.save(roastLevel);
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
                                filter.getSearch(),
                                language, filter.toPageable());
                return PageResponse.of(page.map(r -> buildResponse(r, language)));
        }

        public DashboardRoastLevelResponse getDashboardBySlug(String slug) {
                RoastLevel roastLevel = roastLevelRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(RoastLevelNotFoundException::new);
                return buildResponse(roastLevel);
        }

        public PageResponse<DashboardRoastLevelResponse> getAllDashboard(LookupFilterRequest filter) {
                String search = Objects.requireNonNullElse(
                                filter.getSearch(),
                                "");
                Page<RoastLevel> page = roastLevelRepository.findAllDashboard(
                                search,
                               filter.toPageable());
                return PageResponse.of(page.map(this::buildResponse));
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
        public PageResponse<DashboardRoastLevelResponse> getAllDeleted(LookupFilterRequest filter) {
                String search = Objects.requireNonNullElse(filter.getSearch(), "");
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
         * Un-delete a RoastLevel.
         *
         * <p>Reads through {@code findDeletedById}, so an id that is live or unknown raises
         * {@link RoastLevelNotFoundException} rather than reporting a restore that changed nothing.
         */
        @Transactional
        public void restore(Long id) {
                RoastLevel roastLevel = roastLevelRepository.findDeletedById(id)
                                .orElseThrow(RoastLevelNotFoundException::new);
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
                RoastLevel roastLevel = roastLevelRepository.findDeletedById(id)
                                .orElseThrow(RoastLevelNotFoundException::new);
                roastLevelRepository.delete(roastLevel);
                log.info("RoastLevel permanently deleted: {}", roastLevel.getSlug());
        }
}
