package com.nocteon.nocteon_api.coffeeVariety.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.coffeeVariety.dto.request.CoffeeVarietyRequest;
import com.nocteon.nocteon_api.coffeeVariety.dto.request.CoffeeVarietyTranslationRequest;
import com.nocteon.nocteon_api.coffeeVariety.dto.response.CoffeeVarietyResponse;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVariety;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVarietyTranslation;
import com.nocteon.nocteon_api.coffeeVariety.repository.CoffeeVarietyRepository;
import com.nocteon.nocteon_api.coffeeVariety.repository.CoffeeVarietyTranslationRepository;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.CoffeeVarietyNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoffeeVarietyService {

        private final CoffeeVarietyRepository coffeeVarietyRepository;
        private final CoffeeVarietyTranslationRepository translationRepository;
        private final LookupServiceHelper helper;

        @Transactional
        public CoffeeVarietyResponse create(CoffeeVarietyRequest request) {
                helper.validateTranslations(request.getTranslations());

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(CoffeeVarietyTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                String slug = helper.generateUniqueSlug(englishName, coffeeVarietyRepository::existsBySlug);

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

                return buildResponse(coffeeVariety, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public CoffeeVarietyResponse update(String slug, CoffeeVarietyRequest request) {
                CoffeeVariety coffeeVariety = coffeeVarietyRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(CoffeeVarietyNotFoundException::new);

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
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
                return buildResponse(coffeeVariety, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public void delete(String slug) {
                CoffeeVariety coffeeVariety = coffeeVarietyRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(CoffeeVarietyNotFoundException::new);
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
                                language, filter.getSearch(), filter.toPageable());
                return PageResponse.of(page.map(c -> buildResponse(c, language)));
        }

        public PageResponse<CoffeeVarietyResponse> getAllDashboard(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();

                Page<CoffeeVariety> page = coffeeVarietyRepository.findAllDashboard(
                                filter.getSearch(),
                                filter.toPageable());

                return PageResponse.of(page.map(c -> buildResponse(c, language)));
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
                                .build();
        }
}