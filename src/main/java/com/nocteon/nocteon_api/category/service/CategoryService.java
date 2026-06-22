package com.nocteon.nocteon_api.category.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.category.dto.request.CategoryRequest;
import com.nocteon.nocteon_api.category.dto.request.CreateCategoryTranslationRequest;
import com.nocteon.nocteon_api.category.dto.response.CategoryResponse;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.entity.CategoryTranslation;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.category.repository.CategoryTranslationRepository;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.CategoryNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

        private final CategoryRepository categoryRepository;
        private final CategoryTranslationRepository categoryTranslationRepository;
        private final LookupServiceHelper helper;

        @Transactional
        public CategoryResponse update(String slug, CategoryRequest request, MultipartFile image) {
                Category category = categoryRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(CategoryNotFoundException::new);

                if (request.getIsActive() != null)
                        category.setIsActive(request.getIsActive());

                if (image != null && !image.isEmpty()) {
                        helper.deleteImageIfExists(category.getImageUrl());
                        category.setImageUrl(helper.uploadImage(image, "categories"));
                }

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
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

                categoryRepository.save(category);
                return buildResponse(category, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public CategoryResponse uploadImage(String slug, MultipartFile file) {
                Category category = categoryRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(CategoryNotFoundException::new);

                helper.deleteImageIfExists(category.getImageUrl());
                category.setImageUrl(helper.uploadImage(file, "categories"));
                categoryRepository.save(category);

                return buildResponse(category, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public void delete(String slug) {
                Category category = categoryRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(CategoryNotFoundException::new);

                helper.deleteImageIfExists(category.getImageUrl());
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
        public CategoryResponse create(CategoryRequest request, MultipartFile image) {
                helper.validateTranslations(request.getTranslations());

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(CreateCategoryTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                String slug = helper.generateUniqueSlug(englishName, categoryRepository::existsBySlug);
                String imageUrl = helper.uploadImage(image, "categories");

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
                return buildResponse(category, LocaleContextHolder.getLocale().getLanguage());
        }

        public PageResponse<CategoryResponse> getAll(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();

                Page<Category> page = categoryRepository.findAllPublic(
                                language,
                                filter.getSearch(),
                                filter.toPageable());

                return PageResponse.of(page.map(c -> buildResponse(c, language)));
        }

        public PageResponse<CategoryResponse> getAllDashboard(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();

                Page<Category> page = categoryRepository.findAllDashboard(
                                filter.getSearch(),
                                filter.getIsActive(),
                                filter.toPageable());

                return PageResponse.of(page.map(c -> buildResponse(c, language)));
        }

        public CategoryResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Category category = categoryRepository.findBySlugAndLanguage(slug, language)
                                .orElseThrow(CategoryNotFoundException::new);
                return buildResponse(category, language);
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
                                .build();
        }
}