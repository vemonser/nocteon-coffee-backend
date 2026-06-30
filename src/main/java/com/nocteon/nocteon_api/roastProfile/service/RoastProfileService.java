package com.nocteon.nocteon_api.roastProfile.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.category.dto.response.DashboardCategoryResponse;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.dto.TranslationResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.RoastProfileNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.roastProfile.dto.request.RoastProfileRequest;
import com.nocteon.nocteon_api.roastProfile.dto.request.RoastProfileTranslationRequest;
import com.nocteon.nocteon_api.roastProfile.dto.response.DashboardRoastProfileResponse;
import com.nocteon.nocteon_api.roastProfile.dto.response.RoastProfileResponse;
import com.nocteon.nocteon_api.roastProfile.entity.RoastProfile;
import com.nocteon.nocteon_api.roastProfile.entity.RoastProfileTranslation;
import com.nocteon.nocteon_api.roastProfile.repository.RoastProfileRepository;
import com.nocteon.nocteon_api.roastProfile.repository.RoastProfileTranslationRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoastProfileService {

        private final RoastProfileRepository roastProfileRepository;
        private final RoastProfileTranslationRepository translationRepository;
        private final LookupServiceHelper helper;

        @Transactional
        public RoastProfileResponse create(RoastProfileRequest request) {
                helper.validateTranslations(request.getTranslations());

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(RoastProfileTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                String slug = helper.generateUniqueSlug(englishName, roastProfileRepository::existsBySlug);

                RoastProfile roastProfile = RoastProfile.builder()
                                .slug(slug)
                                .build();

                roastProfile = roastProfileRepository.save(roastProfile);

                final RoastProfile saved = roastProfile;
                List<RoastProfileTranslation> translations = new ArrayList<>();
                for (RoastProfileTranslationRequest t : request.getTranslations()) {
                        translations.add(RoastProfileTranslation.builder()
                                        .roastProfile(saved)
                                        .language(t.getLanguage())
                                        .name(t.getName())
                                        .description(t.getDescription())
                                        .build());
                }
                translationRepository.saveAll(translations);

                return buildResponse(roastProfile, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public RoastProfileResponse update(String slug, RoastProfileRequest request) {
                RoastProfile roastProfile = roastProfileRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(RoastProfileNotFoundException::new);

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
                        request.getTranslations().forEach(t -> translationRepository
                                        .findByRoastProfileIdAndLanguage(roastProfile.getId(), t.getLanguage())
                                        .ifPresentOrElse(
                                                        existing -> {
                                                                existing.setName(t.getName());
                                                                existing.setDescription(t.getDescription());
                                                                translationRepository.save(existing);
                                                        },
                                                        () -> translationRepository.save(
                                                                        RoastProfileTranslation.builder()
                                                                                        .roastProfile(roastProfile)
                                                                                        .language(t.getLanguage())
                                                                                        .name(t.getName())
                                                                                        .description(t.getDescription())
                                                                                        .build())));
                }

                roastProfileRepository.save(roastProfile);
                return buildResponse(roastProfile, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public void delete(String slug) {
                RoastProfile roastProfile = roastProfileRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(RoastProfileNotFoundException::new);
                roastProfile.softDelete();
                roastProfileRepository.save(roastProfile);
        }

        public RoastProfileResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                RoastProfile roastProfile = roastProfileRepository.findBySlugAndLanguage(slug, language)
                                .orElseThrow(RoastProfileNotFoundException::new);
                return buildResponse(roastProfile, language);
        }

        public PageResponse<RoastProfileResponse> getAll(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Page<RoastProfile> page = roastProfileRepository.findAllPublic(
                                language, filter.getSearch(), filter.toPageable());
                return PageResponse.of(page.map(r -> buildResponse(r, language)));
        }

        public PageResponse<DashboardRoastProfileResponse> getAllDashboard(LookupFilterRequest filter) {
                String search = Objects.requireNonNullElse(
                                filter.getSearch(),
                                "");
                Page<RoastProfile> page = roastProfileRepository.findAllDashboard(
                                search,
                               filter.toPageable());
                return PageResponse.of(page.map(this::buildResponse));
        }

        private RoastProfileResponse buildResponse(RoastProfile roastProfile, String language) {
                List<RoastProfileTranslation> translations = translationRepository
                                .findByRoastProfileId(roastProfile.getId());

                RoastProfileTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                return RoastProfileResponse.builder()
                                .id(roastProfile.getId())
                                .slug(roastProfile.getSlug())
                                .name(translation != null ? translation.getName() : null)
                                .description(translation != null ? translation.getDescription() : null)
                                .build();
        }

        private DashboardRoastProfileResponse buildResponse(RoastProfile roastProfile) {
                return DashboardRoastProfileResponse.builder()
                                .id(roastProfile.getId())
                                .slug(roastProfile.getSlug())
                                .translations(
                                                roastProfile.getTranslations()
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
