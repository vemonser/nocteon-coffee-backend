package com.nocteon.nocteon_api.roastLevel.service;

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
import com.nocteon.nocteon_api.common.exception.notFound.RoastLevelNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.roastLevel.dto.request.RoastLevelRequest;
import com.nocteon.nocteon_api.roastLevel.dto.request.RoastLevelTranslationRequest;
import com.nocteon.nocteon_api.roastLevel.dto.response.DashboardRoastLevelResponse;
import com.nocteon.nocteon_api.roastLevel.dto.response.RoastLevelResponse;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevelTranslation;
import com.nocteon.nocteon_api.roastLevel.repository.RoastLevelRepository;
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

        @Transactional
        public RoastLevelResponse create(RoastLevelRequest request) {
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

                return buildResponse(roastLevel, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public RoastLevelResponse update(String slug, RoastLevelRequest request) {
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
                return buildResponse(roastLevel, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public void delete(String slug) {
                RoastLevel roastLevel = roastLevelRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(RoastLevelNotFoundException::new);
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
                                language, filter.getSearch(), filter.toPageable());
                return PageResponse.of(page.map(r -> buildResponse(r, language)));
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

}
