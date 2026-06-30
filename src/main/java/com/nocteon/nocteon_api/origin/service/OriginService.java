package com.nocteon.nocteon_api.origin.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.category.dto.response.DashboardCategoryResponse;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.dto.TranslationResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.OriginNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.origin.dto.request.OriginRequest;
import com.nocteon.nocteon_api.origin.dto.request.OriginTranslationRequest;
import com.nocteon.nocteon_api.origin.dto.response.DashboardOriginResponse;
import com.nocteon.nocteon_api.origin.dto.response.OriginResponse;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.entity.OriginTranslation;
import com.nocteon.nocteon_api.origin.repository.OriginRepository;
import com.nocteon.nocteon_api.origin.repository.OriginTranslationRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OriginService {

        private final OriginRepository originRepository;
        private final OriginTranslationRepository translationRepository;
        private final LookupServiceHelper helper;

        @Transactional
        public OriginResponse create(OriginRequest request, MultipartFile image) {
                helper.validateTranslations(request.getTranslations());

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(OriginTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                String slug = helper.generateUniqueSlug(englishName, originRepository::existsBySlug);
                String imageUrl = helper.uploadImage(image, "origins");

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

                log.info("Origin created with slug: {}", slug);
                return buildResponse(origin, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public OriginResponse update(String slug, OriginRequest request, MultipartFile image) {
                Origin origin = originRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(OriginNotFoundException::new);

                if (request.getCode() != null)
                        origin.setCode(request.getCode());

                if (image != null && !image.isEmpty()) {
                        helper.deleteImageIfExists(origin.getImageUrl());
                        origin.setImageUrl(helper.uploadImage(image, "origins"));
                }

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
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
                return buildResponse(origin, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public OriginResponse uploadImage(String slug, MultipartFile file) {
                Origin origin = originRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(OriginNotFoundException::new);

                helper.deleteImageIfExists(origin.getImageUrl());
                origin.setImageUrl(helper.uploadImage(file, "origins"));
                originRepository.save(origin);

                return buildResponse(origin, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public void delete(String slug) {
                Origin origin = originRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(OriginNotFoundException::new);

                helper.deleteImageIfExists(origin.getImageUrl());
                origin.softDelete();
                originRepository.save(origin);
                log.info("Origin soft deleted: {}", slug);
        }

        public OriginResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Origin origin = originRepository.findBySlugAndLanguage(slug, language)
                                .orElseThrow(OriginNotFoundException::new);
                return buildResponse(origin, language);
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
                                language,
                                filter.getSearch(),
                                filter.toPageable());
                return PageResponse.of(page.map(o -> buildResponse(o, language)));
        }

        public PageResponse<DashboardOriginResponse> getAllDashboard(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                String search = Objects.requireNonNullElse(
                                filter.getSearch(),
                                "");

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

}
