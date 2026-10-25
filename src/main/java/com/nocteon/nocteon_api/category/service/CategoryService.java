package com.nocteon.nocteon_api.category.service;

import java.time.Instant;

import com.nocteon.nocteon_api.product.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.category.dto.request.CategoryRequest;
import com.nocteon.nocteon_api.category.dto.request.CreateCategoryTranslationRequest;
import com.nocteon.nocteon_api.category.dto.response.CategoryResponse;
import com.nocteon.nocteon_api.category.dto.response.DashboardCategoryResponse;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.entity.CategoryTranslation;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.category.repository.CategoryTranslationRepository;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.dto.TranslationResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.invalid.EntityInUseException;
import com.nocteon.nocteon_api.common.exception.product.DuplicateTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.CategoryNotFoundException;
import com.nocteon.nocteon_api.common.service.SoftDeleteRowFolder;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.enums.MediaType;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

        private final ProductRepository productRepository;
        private final CategoryRepository categoryRepository;
        private final CategoryTranslationRepository categoryTranslationRepository;
        private final LookupServiceHelper helper;
        private final ProductResponseMapper productResponseMapper;

        @Transactional(readOnly = true)
        public Page<ProductCardResponse> getProductsByCategorySlug(String slug, Pageable pageable) {
                if (!categoryRepository.existsBySlug(slug)) {
                        throw new CategoryNotFoundException();
                }
                String language = LocaleContextHolder.getLocale().getLanguage();
                return productRepository.findByCategorySlugPublic(slug, pageable)
                                .map(p -> productResponseMapper.buildListResponse(p, language));
        }

        @Transactional
        public DashboardCategoryResponse update(String slug, CategoryRequest request, MultipartFile image) {
                Category category = categoryRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(CategoryNotFoundException::new);

                if (request.getIsActive() != null)
                        category.setIsActive(request.getIsActive());

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());

                        request.getTranslations().stream()
                                        .sorted(java.util.Comparator.comparing(
                                                        com.nocteon.nocteon_api.category.dto.request.CreateCategoryTranslationRequest::getLanguage))
                                        .forEach(t -> {
                                                String normalized = helper.normalizeName(t.getName());
                                                helper.acquireNameLock("category", t.getLanguage(), normalized);
                                                if (categoryTranslationRepository.existsLiveTranslationName(
                                                                t.getLanguage(), t.getName(), category.getId())) {
                                                        throw new DuplicateTranslationException();
                                                }
                                        });

                        request.getTranslations().forEach(t -> categoryTranslationRepository
                                        .findByCategoryIdAndLanguage(category.getId(), t.getLanguage())
                                        .ifPresentOrElse(
                                                        existing -> {
                                                                existing.setName(t.getName());
                                                                existing.setDescription(t.getDescription());
                                                                categoryTranslationRepository.save(existing);
                                                        },
                                                        () -> categoryTranslationRepository.save(
                                                                        CategoryTranslation.builder()
                                                                                        .category(category)
                                                                                        .language(t.getLanguage())
                                                                                        .name(t.getName())
                                                                                        .description(t.getDescription())
                                                                                        .build())));
                }

                if (image != null && !image.isEmpty()) {
                        helper.deleteMediaIfExists(category.getImageUrl());
                        category.setImageUrl(helper.uploadMedia(image, "categories", MediaType.IMAGE));
                }

                categoryRepository.save(category);
                var reloaded = categoryRepository.findBySlugWithTranslations(category.getSlug()).orElse(category);

                return buildResponse(reloaded);
        }

        @Transactional
        public DashboardCategoryResponse uploadImage(String slug, MultipartFile file) {
                Category category = categoryRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(CategoryNotFoundException::new);

                helper.deleteMediaIfExists(category.getImageUrl());
                category.setImageUrl(helper.uploadMedia(file, "categories", MediaType.IMAGE));
                categoryRepository.save(category);

                var reloaded = categoryRepository.findBySlugWithTranslations(slug).orElse(category);


                return buildResponse(reloaded);
        }

        @Transactional
        public void delete(String slug) {
                Category category = categoryRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(CategoryNotFoundException::new);

                if (productRepository.countByCategoryId(category.getId()) > 0) {
                        throw new EntityInUseException();
                }

                category.softDelete();
                categoryRepository.save(category);
                log.info("Category soft deleted: {}", slug);
        }

        public CategoryResponse getCategory(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Category category = categoryRepository.findBySlugAndLanguage(slug, language)
                                .orElseThrow(CategoryNotFoundException::new);
                return buildResponse(category, language);
        }

        @Transactional
        public DashboardCategoryResponse create(CategoryRequest request, MultipartFile image) {
                helper.validateTranslations(request.getTranslations());

                request.getTranslations().stream()
                                .sorted(java.util.Comparator.comparing(
                                                com.nocteon.nocteon_api.category.dto.request.CreateCategoryTranslationRequest::getLanguage))
                                .forEach(t -> {
                                        String normalized = helper.normalizeName(t.getName());
                                        helper.acquireNameLock("category", t.getLanguage(), normalized);
                                        if (categoryTranslationRepository.existsLiveTranslationName(
                                                        t.getLanguage(), t.getName(), null)) {
                                                throw new DuplicateTranslationException();
                                        }
                                });

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(CreateCategoryTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                String slug = helper.generateUniqueSlug(englishName, categoryRepository::existsBySlugIncludingDeleted);
                String imageUrl = helper.uploadMedia(image, "categories", MediaType.IMAGE);

                Category category = Category.builder()
                                .slug(slug)
                                .imageUrl(imageUrl)
                                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                                .build();

                category = categoryRepository.save(category);

                final Category savedCategory = category;
                List<CategoryTranslation> translations = new ArrayList<>();
                for (CreateCategoryTranslationRequest t : request.getTranslations()) {
                        translations.add(CategoryTranslation.builder()
                                        .category(savedCategory)
                                        .language(t.getLanguage())
                                        .name(t.getName())
                                        .description(t.getDescription())
                                        .build());
                }
                categoryTranslationRepository.saveAll(translations);

                log.info("Category created with slug: {}", slug);
                var reloaded = categoryRepository.findBySlugWithTranslations(slug).orElse(category);

                return buildResponse(reloaded);
        }

        public PageResponse<CategoryResponse> getAll(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                String search = SearchPatterns.like(filter.getSearch());

                Page<Category> page = categoryRepository.findAllPublic(
                                language,
                                search,
                                filter.toPageable());

                return PageResponse.of(page.map(c -> buildResponse(c, language)));
        }

        public PageResponse<DashboardCategoryResponse> getAllDashboard(
                        LookupFilterRequest filter) {

                String search = SearchPatterns.like(filter.getSearch());

                Page<Category> page = categoryRepository.findAllDashboard(
                                search,
                                filter.getIsActive(),
                                filter.toPageable());

                return PageResponse.of(
                                page.map(this::buildResponse));
        }

        public CategoryResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Category category = categoryRepository.findBySlugAndLanguage(slug, language)
                                .orElseThrow(CategoryNotFoundException::new);
                return buildResponse(category, language);
        }

        public DashboardCategoryResponse getDashboardBySlug(String slug) {
                Category category = categoryRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(CategoryNotFoundException::new);
                return buildResponse(category);
        }

        private CategoryResponse buildResponse(Category category, String language) {
                List<CategoryTranslation> translations = categoryTranslationRepository
                                .findByCategoryId(category.getId());

                CategoryTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                return CategoryResponse.builder()
                                .id(category.getId())
                                .slug(category.getSlug())
                                .name(translation != null ? translation.getName() : null)
                                .description(translation != null ? translation.getDescription() : null)
                                .isActive(category.getIsActive())
                                .imageUrl(category.getImageUrl())
                                .createdAt(category.getCreatedAt())
                                .build();
        }

        private DashboardCategoryResponse buildResponse(Category category) {
                return DashboardCategoryResponse.builder()
                                .id(category.getId())
                                .slug(category.getSlug())
                                .imageUrl(category.getImageUrl())
                                .isActive(category.getIsActive())
                                .createdAt(category.getCreatedAt())
                                .translations(
                                                category.getTranslations()
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
         * Trashed Categorys for the dashboard's deleted-items table.
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
        public PageResponse<DashboardCategoryResponse> getAllDeleted(LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());
                int pageNum = filter.getPage();
                int size = filter.getSize();
                String sort = filter.getSort();
                String direction = filter.getDirection();

                List<Object[]> rawResults = categoryRepository.findDeletedBySearchNative(search);

                List<DashboardCategoryResponse> allResults = SoftDeleteRowFolder.fold(
                                rawResults,
                                row -> ((Number) row[0]).longValue(),
                                this::mapToDashboardCategoryResponse,
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
                List<DashboardCategoryResponse> pagedResults = allResults.subList(fromIndex, toIndex);

                Page<DashboardCategoryResponse> page = new org.springframework.data.domain.PageImpl<>(
                                pagedResults,
                                org.springframework.data.domain.PageRequest.of(pageNum, size),
                                allResults.size());
                return PageResponse.of(page);
        }

        private DashboardCategoryResponse mapToDashboardCategoryResponse(Object[] row) {
                return DashboardCategoryResponse.builder()
                                .id(((Number) row[0]).longValue())
                                .slug((String) row[1])
                                .imageUrl((String) row[2])
                                .isActive((Boolean) row[3])
                                .createdAt((Instant) row[4])
                                .deletedAt(row.length > 8 ? (Instant) row[8] : null)
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
         * Un-delete a Category.
         *
         * <p>Reads through {@code findDeletedById}, so an id that is live or unknown raises
         * {@link CategoryNotFoundException} rather than reporting a restore that changed nothing.
         */
        @Transactional
        public void restore(Long id) {
                Category category = categoryRepository.findDeletedById(id)
                                .orElseThrow(CategoryNotFoundException::new);

                List<CategoryTranslation> translations = categoryTranslationRepository.findByCategoryId(category.getId());
                translations.stream()
                                .sorted(java.util.Comparator.comparing(CategoryTranslation::getLanguage))
                                .forEach(t -> {
                                        String normalized = helper.normalizeName(t.getName());
                                        helper.acquireNameLock("category", t.getLanguage(), normalized);
                                        if (categoryTranslationRepository.existsLiveTranslationName(
                                                        t.getLanguage(), t.getName(), category.getId())) {
                                                throw new DuplicateTranslationException();
                                        }
                                });

                category.setDeletedAt(null);
                categoryRepository.save(category);
                log.info("Category restored: {}", category.getSlug());
        }

        /**
         * Permanently deletes a trashed Category: removes its image from Cloudinary, then the row itself.
         *
         * <p>Deletes the row — and flushes, so a foreign-key conflict surfaces here — <em>before</em>
         * touching Cloudinary. If the database refuses the delete, the trashed row and its image
         * are both untouched and the admin can retry; the reverse order would strand a restored
         * item without its picture.
         *
         * <p>Trash-only: reads through {@code findDeletedById}, so a live or unknown id raises
         * {@link CategoryNotFoundException} instead of deleting anything. Soft-delete first — this never touches live
         * rows.
         */
        @Transactional
        public void permanentDelete(Long id) {
                Category category = categoryRepository.findDeletedById(id)
                                .orElseThrow(CategoryNotFoundException::new);
                categoryRepository.delete(category);
                categoryRepository.flush();
                helper.deleteMediaIfExists(category.getImageUrl());
                log.info("Category permanently deleted: {}", category.getSlug());
        }
}
