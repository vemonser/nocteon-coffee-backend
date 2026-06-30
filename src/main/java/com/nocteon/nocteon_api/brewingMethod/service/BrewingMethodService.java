package com.nocteon.nocteon_api.brewingMethod.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
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
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrewingMethodService {

        private final BrewingMethodRepository brewingMethodRepository;
        private final BrewingMethodTranslationRepository translationRepository;
        private final LookupServiceHelper helper;

        @Transactional
        public BrewingMethodResponse create(BrewingMethodRequest request) {
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

                return buildResponse(brewingMethod, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public BrewingMethodResponse update(String slug, BrewingMethodRequest request) {
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
                return buildResponse(brewingMethod, LocaleContextHolder.getLocale().getLanguage());
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
                                language, filter.getSearch(), filter.toPageable());
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
                                .name(translation != null ? translation.getName() : null)
                                .description(translation != null ? translation.getDescription() : null)
                                .build();
        }

                private BrewingMethodResponseDashboard buildResponse(BrewingMethod brewingMethod) {
                return BrewingMethodResponseDashboard.builder()
                                .id(brewingMethod.getId())
                                .slug(brewingMethod.getSlug())
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

}