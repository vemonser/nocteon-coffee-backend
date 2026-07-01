package com.nocteon.nocteon_api.farm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.category.dto.response.DashboardCategoryResponse;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.dto.TranslationResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.FarmNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.OriginNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.farm.dto.request.FarmFilterRequest;
import com.nocteon.nocteon_api.farm.dto.request.FarmRequest;
import com.nocteon.nocteon_api.farm.dto.request.FarmTranslationRequest;
import com.nocteon.nocteon_api.farm.dto.response.DashboardFarmResponse;
import com.nocteon.nocteon_api.farm.dto.response.FarmResponse;
import com.nocteon.nocteon_api.farm.dto.response.FarmTranslationResponse;
import com.nocteon.nocteon_api.farm.entity.Farm;
import com.nocteon.nocteon_api.farm.entity.FarmTranslation;
import com.nocteon.nocteon_api.farm.repository.FarmRepository;
import com.nocteon.nocteon_api.farm.repository.FarmTranslationRepository;

import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.repository.OriginRepository;
import com.nocteon.nocteon_api.product.enums.MediaType;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FarmService {

    private final FarmRepository farmRepository;
    private final FarmTranslationRepository farmTranslationRepository;
    private final OriginRepository originRepository;
    private final LookupServiceHelper helper;

    public PageResponse<FarmResponse> getAll(FarmFilterRequest filter) {
        String language = LocaleContextHolder.getLocale().getLanguage();
        Page<Farm> page = farmRepository.findAllPublic(
                language,
                filter.getSearch(),
                filter.getOriginSlug(),
                filter.toPageable());
        return PageResponse.of(page.map(f -> buildResponse(f, language)));
    }

    public PageResponse<DashboardFarmResponse> getAllDashboard(FarmFilterRequest filter) {

        String search = Objects.requireNonNullElse(
                                filter.getSearch(),
                                "");
        Page<Farm> page = farmRepository.findAllDashboard(
                search,
                filter.getOriginSlug(),
                filter.toPageable());
        return PageResponse.of(page.map(this::buildResponse));
    }

    public FarmResponse getBySlug(String slug) {
        String language = LocaleContextHolder.getLocale().getLanguage();
        Farm farm = farmRepository.findBySlugAndLanguage(slug, language)
                .orElseThrow(FarmNotFoundException::new);
        return buildResponse(farm, language);
    }

    @Transactional
    public FarmResponse create(FarmRequest request, MultipartFile image) {
        helper.validateTranslations(request.getTranslations());

        Origin origin = originRepository.findBySlug(request.getOriginSlug())
                .orElseThrow(OriginNotFoundException::new);

        String englishName = request.getTranslations().stream()
                .filter(t -> t.getLanguage().equals("en"))
                .findFirst()
                .map(FarmTranslationRequest::getName)
                .orElseThrow(InvalidTranslationException::new);

        String slug = helper.generateUniqueSlug(englishName, farmRepository::existsBySlug);
        String imageUrl = helper.uploadMedia(image, "farms",MediaType.IMAGE);

        Farm farm = Farm.builder()
                .origin(origin)
                .slug(slug)
                .imageUrl(imageUrl)
                .build();

        farm = farmRepository.save(farm);

        final Farm saved = farm;
        List<FarmTranslation> translations = new ArrayList<>();
        for (FarmTranslationRequest t : request.getTranslations()) {
            translations.add(FarmTranslation.builder()
                    .farm(saved)
                    .language(t.getLanguage())
                    .name(t.getName())
                    .country(t.getCountry())
                    .description(t.getDescription())
                    .build());
        }
        farmTranslationRepository.saveAll(translations);

        log.info("Farm created with slug: {}", slug);
        return buildResponse(farm, LocaleContextHolder.getLocale().getLanguage());
    }

    @Transactional
    public FarmResponse update(String slug, FarmRequest request, MultipartFile image) {
        Farm farm = farmRepository.findBySlugWithTranslations(slug)
                .orElseThrow(FarmNotFoundException::new);

        if (request.getOriginSlug() != null) {
            Origin origin = originRepository.findBySlug(request.getOriginSlug())
                    .orElseThrow(OriginNotFoundException::new);
            farm.setOrigin(origin);
        }

        if (image != null && !image.isEmpty()) {
            helper.deleteMediaIfExists(farm.getImageUrl());
            farm.setImageUrl(helper.uploadMedia(image, "farms",MediaType.IMAGE));
        }

        if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
            helper.validateTranslations(request.getTranslations());
            request.getTranslations().forEach(t ->
                farmTranslationRepository
                        .findByFarmIdAndLanguage(farm.getId(), t.getLanguage())
                        .ifPresentOrElse(
                                existing -> {
                                    existing.setName(t.getName());
                                    existing.setCountry(t.getCountry());
                                    existing.setDescription(t.getDescription());
                                    farmTranslationRepository.save(existing);
                                },
                                () -> farmTranslationRepository.save(
                                        FarmTranslation.builder()
                                                .farm(farm)
                                                .language(t.getLanguage())
                                                .name(t.getName())
                                                .country(t.getCountry())
                                                .description(t.getDescription())
                                                .build())
                        )
            );
        }

        farmRepository.save(farm);
        return buildResponse(farm, LocaleContextHolder.getLocale().getLanguage());
    }

    @Transactional
    public FarmResponse uploadImage(String slug, MultipartFile file) {
        Farm farm = farmRepository.findBySlugWithTranslations(slug)
                .orElseThrow(FarmNotFoundException::new);

        helper.deleteMediaIfExists(farm.getImageUrl());
        farm.setImageUrl(helper.uploadMedia(file, "farms",MediaType.IMAGE));
        farmRepository.save(farm);

        return buildResponse(farm, LocaleContextHolder.getLocale().getLanguage());
    }

    @Transactional
    public void delete(String slug) {
        Farm farm = farmRepository.findBySlugWithTranslations(slug)
                .orElseThrow(FarmNotFoundException::new);
        helper.deleteMediaIfExists(farm.getImageUrl());
        farm.softDelete();
        farmRepository.save(farm);
        log.info("Farm soft deleted: {}", slug);
    }

    private FarmResponse buildResponse(Farm farm, String language) {
        List<FarmTranslation> translations = farmTranslationRepository
                .findByFarmId(farm.getId());

        FarmTranslation translation = translations.stream()
                .filter(t -> t.getLanguage().equals(language))
                .findFirst()
                .orElse(translations.isEmpty() ? null : translations.get(0));

        return FarmResponse.builder()
                .id(farm.getId())
                .originSlug(farm.getOrigin().getSlug())
                .slug(farm.getSlug())
                .name(translation != null ? translation.getName() : null)
                .country(translation != null ? translation.getCountry() : null)
                .description(translation != null ? translation.getDescription() : null)
                .imageUrl(farm.getImageUrl())
                .build();
    }



            private DashboardFarmResponse buildResponse(Farm farm) {
                return DashboardFarmResponse.builder()
                                .id(farm.getId())
                                .originSlug(farm.getOrigin().getSlug())
                                .slug(farm.getSlug())
                                .imageUrl(farm.getImageUrl())
                                .translations(
                                                farm.getTranslations()
                                                                .stream()
                                                                .map(t -> FarmTranslationResponse.builder()
                                                                                .language(t.getLanguage())
                                                                                .name(t.getName())
                                                                                .description(t.getDescription())
                                                                                .country(t.getCountry())
                                                                                .build())
                                                                .toList())
                                .build();
        }
}