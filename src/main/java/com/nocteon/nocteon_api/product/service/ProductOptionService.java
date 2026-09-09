package com.nocteon.nocteon_api.product.service;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.dto.TranslationResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductOptionNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductOptionValueNotFoundException;

import com.nocteon.nocteon_api.product.dto.request.ProductOptionRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductOptionValueRequest;
import com.nocteon.nocteon_api.product.dto.request.TranslationRequest;
import com.nocteon.nocteon_api.product.dto.request.ValueTranslationRequest;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionResponseDashboard;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionValueResponseDashboard;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductOption;
import com.nocteon.nocteon_api.product.entity.ProductOptionTranslation;
import com.nocteon.nocteon_api.product.entity.ProductOptionValue;
import com.nocteon.nocteon_api.product.entity.ProductOptionValueTranslation;
import com.nocteon.nocteon_api.product.repository.ProductOptionRepository;
import com.nocteon.nocteon_api.product.repository.ProductOptionTranslationRepository;
import com.nocteon.nocteon_api.product.repository.ProductOptionValueRepository;
import com.nocteon.nocteon_api.product.repository.ProductOptionValueTranslationRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductOptionService {

    private final ProductOptionRepository optionRepository;
    private final ProductOptionTranslationRepository optionTranslationRepository;
    private final ProductOptionValueRepository valueRepository;
    private final ProductOptionValueTranslationRepository valueTranslationRepository;
    private final ProductRepository productRepository;

    // ===== Options CRUD =====

    @Transactional
    public ProductOptionResponse create(ProductOptionRequest request) {
        validateNameTranslations(request.getTranslations());

        String englishName = request.getTranslations().stream()
                .filter(t -> t.getLanguage().equals("en"))
                .findFirst()
                .map(TranslationRequest::getName)
                .orElseThrow(InvalidTranslationException::new);

        String slug = generateUniqueSlug(englishName, optionRepository::existsBySlug);

        ProductOption option = ProductOption.builder()
                .slug(slug)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        option = optionRepository.save(option);

        List<ProductOptionTranslation> translations = new ArrayList<>();
        for (TranslationRequest t : request.getTranslations()) {
            translations.add(ProductOptionTranslation.builder()
                    .option(option)
                    .language(t.getLanguage())
                    .name(t.getName())
                    .build());
        }
        optionTranslationRepository.saveAll(translations);

        return buildResponse(option, LocaleContextHolder.getLocale().getLanguage());
    }

    @Transactional
    public ProductOptionResponse update(Long optionId, ProductOptionRequest request) {
        ProductOption option = optionRepository.findById(optionId)
                .orElseThrow(ProductOptionNotFoundException::new);

        if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
            validateNameTranslations(request.getTranslations());

            for (TranslationRequest t : request.getTranslations()) {
                optionTranslationRepository
                        .findByOptionIdAndLanguage(option.getId(), t.getLanguage())
                        .ifPresentOrElse(
                                existing -> {
                                    existing.setName(t.getName());
                                    optionTranslationRepository.save(existing);
                                },
                                () -> optionTranslationRepository.save(
                                        ProductOptionTranslation.builder()
                                                .option(option)
                                                .language(t.getLanguage())
                                                .name(t.getName())
                                                .build()));
            }
        }

        if (request.getSortOrder() != null) {
            option.setSortOrder(request.getSortOrder());
        }

        optionRepository.save(option);
        return buildResponse(option, LocaleContextHolder.getLocale().getLanguage());
    }

    @Transactional
    public void delete(Long optionId) {
        ProductOption option = optionRepository.findById(optionId)
                .orElseThrow(ProductOptionNotFoundException::new);
        optionRepository.delete(option);
    }

    public PageResponse<ProductOptionResponseDashboard> getAllDashboard(LookupFilterRequest filter) {
        String search = Objects.requireNonNullElse(filter.getSearch(), "");
        Page<ProductOption> page = optionRepository.findAllDashboard(search, filter.toPageable());
        return PageResponse.of(page.map(this::buildDashboardResponse));
    }

    public ProductOptionResponseDashboard getDashboardById(Long optionId) {
        ProductOption option = optionRepository.findById(optionId)
                .orElseThrow(ProductOptionNotFoundException::new);
        return buildDashboardResponse(option);
    }

    // ===== Values CRUD (nested under an option) =====

    @Transactional
    public ProductOptionValueResponseDashboard addValue(Long optionId, ProductOptionValueRequest request) {
        ProductOption option = optionRepository.findById(optionId)
                .orElseThrow(ProductOptionNotFoundException::new);

        validateValueTranslations(request.getTranslations());

        String englishValue = request.getTranslations().stream()
                .filter(t -> t.getLanguage().equals("en"))
                .findFirst()
                .map(ValueTranslationRequest::getValue)
                .orElseThrow(InvalidTranslationException::new);

        String slug = generateUniqueSlug(englishValue,
                candidateSlug -> valueRepository.existsByOptionIdAndSlug(optionId, candidateSlug));

        ProductOptionValue value = ProductOptionValue.builder()
                .option(option)
                .slug(slug)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .numericValue(request.getNumericValue())
                .build();

        value = valueRepository.save(value);

        List<ProductOptionValueTranslation> translations = new ArrayList<>();
        for (ValueTranslationRequest t : request.getTranslations()) {
            translations.add(ProductOptionValueTranslation.builder()
                    .optionValue(value)
                    .language(t.getLanguage())
                    .value(t.getValue())
                    .build());
        }
        valueTranslationRepository.saveAll(translations);

        return buildValueDashboardResponse(value);
    }

    @Transactional
    public void deleteValue(Long optionId, Long valueId) {
        ProductOptionValue value = valueRepository.findById(valueId)
                .filter(v -> v.getOption().getId().equals(optionId))
                .orElseThrow(ProductOptionValueNotFoundException::new);
        valueRepository.delete(value);
    }

    // ===== Linking a product to an option =====

    @Transactional
    public void linkToProduct(Long productId, Long optionId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(ProductNotFoundException::new);
        ProductOption option = optionRepository.findById(optionId)
                .orElseThrow(ProductOptionNotFoundException::new);

        product.getOptions().add(option);
        productRepository.save(product);
    }

    @Transactional
    public void unlinkFromProduct(Long productId, Long optionId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(ProductNotFoundException::new);

        product.getOptions().removeIf(o -> o.getId().equals(optionId));
        productRepository.save(product);
    }

    // ===== Validation (local, independent from LookupServiceHelper) =====

    private void validateNameTranslations(List<TranslationRequest> translations) {
        boolean hasEnglish = translations.stream().anyMatch(t -> t.getLanguage().equals("en"));
        if (!hasEnglish) {
            throw new InvalidTranslationException();
        }

        long distinctLanguages = translations.stream().map(TranslationRequest::getLanguage).distinct().count();
        if (distinctLanguages != translations.size()) {
            throw new InvalidTranslationException();
        }
    }

    private void validateValueTranslations(List<ValueTranslationRequest> translations) {
        boolean hasEnglish = translations.stream().anyMatch(t -> t.getLanguage().equals("en"));
        if (!hasEnglish) {
            throw new InvalidTranslationException();
        }

        long distinctLanguages = translations.stream().map(ValueTranslationRequest::getLanguage).distinct().count();
        if (distinctLanguages != translations.size()) {
            throw new InvalidTranslationException();
        }
    }

    private String generateUniqueSlug(String name, java.util.function.Predicate<String> existsCheck) {
        String base = name.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-");

        String candidate = base;
        int suffix = 1;
        while (existsCheck.test(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    // ===== Builders =====

    private ProductOptionResponse buildResponse(ProductOption option, String language) {
        String name = option.getTranslations().stream()
                .filter(t -> t.getLanguage().equals(language))
                .findFirst()
                .map(ProductOptionTranslation::getName)
                .orElse(option.getSlug());

        return ProductOptionResponse.builder()
                .id(option.getId())
                .slug(option.getSlug())
                .sortOrder(option.getSortOrder())
                .createdAt(option.getCreatedAt())
                .name(name)
                .build();
    }

    private ProductOptionResponseDashboard buildDashboardResponse(ProductOption option) {
        return ProductOptionResponseDashboard.builder()
                .id(option.getId())
                .slug(option.getSlug())
                .sortOrder(option.getSortOrder())
                .createdAt(option.getCreatedAt())
                .translations(option.getTranslations().stream()
                        .map(t -> TranslationResponse.builder()
                                .language(t.getLanguage())
                                .name(t.getName())
                                .build())
                        .toList())
                .values(option.getValues().stream()
                        .map(this::buildValueDashboardResponse)
                        .toList())
                .build();
    }

    private ProductOptionValueResponseDashboard buildValueDashboardResponse(ProductOptionValue value) {
        return ProductOptionValueResponseDashboard.builder()
                .id(value.getId())
                .slug(value.getSlug())
                .sortOrder(value.getSortOrder())
                .numericValue(value.getNumericValue())
                .translations(value.getTranslations().stream()
                        .map(t -> TranslationResponse.builder()
                                .language(t.getLanguage())
                                .name(t.getValue())
                                .build())
                        .toList())
                .build();
    }
}