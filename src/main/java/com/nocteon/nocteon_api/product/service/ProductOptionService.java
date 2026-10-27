package com.nocteon.nocteon_api.product.service;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.dto.TranslationResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductOptionNotFoundException;
import com.nocteon.nocteon_api.common.exception.product.OptionInUseException;
import com.nocteon.nocteon_api.common.exception.product.OptionValueInUseException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductOptionValueNotFoundException;
import com.nocteon.nocteon_api.common.exception.invalid.FieldValidationException;
import com.nocteon.nocteon_api.common.exception.product.ValueExistsException;
import com.nocteon.nocteon_api.common.dto.ApiFieldError;

import com.nocteon.nocteon_api.product.dto.request.ProductOptionRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductOptionValueRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductOptionValuePatchRequest;
import com.nocteon.nocteon_api.product.dto.request.TranslationRequest;
import com.nocteon.nocteon_api.product.dto.request.ValueTranslationRequest;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionResponseDashboard;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionValueResponseDashboard;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductOption;
import com.nocteon.nocteon_api.product.entity.ProductOptionTranslation;
import com.nocteon.nocteon_api.product.entity.ProductOptionValue;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.entity.ProductOptionValueTranslation;
import com.nocteon.nocteon_api.product.repository.ProductOptionRepository;
import com.nocteon.nocteon_api.product.repository.ProductOptionTranslationRepository;
import com.nocteon.nocteon_api.product.repository.ProductOptionValueRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.product.repository.ProductOptionValueTranslationRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

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
    private final ProductVariantRepository productVariantRepository;

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

        Map<Long, Long> counts = countVariantsByOptionIds(List.of(optionId));
        int variantCount = counts.getOrDefault(optionId, 0L).intValue();
        if (variantCount > 0) {
            throw new OptionInUseException(variantCount);
        }

        for (ProductOptionValue v : option.getValues()) {
            productVariantRepository.removeValueFromVariants(v.getId());
        }

        optionRepository.delete(option);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductOptionResponseDashboard> getAllDashboard(LookupFilterRequest filter) {
        return getAllDashboard(filter, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductOptionResponseDashboard> getAllDashboard(LookupFilterRequest filter, ProductType productType) {
        String search = SearchPatterns.like(filter.getSearch());
        Page<ProductOption> page = optionRepository.findAllDashboard(search, productType, filter.toPageable());

        List<Long> optionIds = page.getContent().stream().map(ProductOption::getId).toList();
        Map<Long, Long> optionCounts = countVariantsByOptionIds(optionIds);

        List<Long> valueIds = page.getContent().stream()
                .flatMap(o -> o.getValues().stream())
                .map(ProductOptionValue::getId)
                .toList();
        Map<Long, Long> valueCounts = countVariantsByValueIds(valueIds);

        PageResponse<ProductOptionResponseDashboard> response = PageResponse.of(page.map(this::buildDashboardResponse));
        for (ProductOptionResponseDashboard r : response.getContent()) {
            r.setUsedByVariants(optionCounts.getOrDefault(r.getId(), 0L).intValue());
            for (ProductOptionValueResponseDashboard v : r.getValues()) {
                v.setUsedByVariants(valueCounts.getOrDefault(v.getId(), 0L).intValue());
            }
        }
        return response;
    }

    public ProductOptionResponseDashboard getDashboardById(Long optionId) {
        ProductOption option = optionRepository.findById(optionId)
                .orElseThrow(ProductOptionNotFoundException::new);
        ProductOptionResponseDashboard response = buildDashboardResponse(option);
        Map<Long, Long> optionCounts = countVariantsByOptionIds(List.of(optionId));
        response.setUsedByVariants(optionCounts.getOrDefault(optionId, 0L).intValue());
        List<Long> valueIds = option.getValues().stream().map(ProductOptionValue::getId).toList();
        Map<Long, Long> valueCounts = countVariantsByValueIds(valueIds);
        for (ProductOptionValueResponseDashboard v : response.getValues()) {
            v.setUsedByVariants(valueCounts.getOrDefault(v.getId(), 0L).intValue());
        }
        return response;
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

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public long getValueUsageCount(Long optionId, Long valueId) {
        valueRepository.findById(valueId)
                .filter(v -> v.getOption().getId().equals(optionId))
                .orElseThrow(ProductOptionValueNotFoundException::new);
        Map<Long, Long> counts = countVariantsByValueIds(List.of(valueId));
        return counts.getOrDefault(valueId, 0L);
    }

    @Transactional
    public void deleteValue(Long optionId, Long valueId) {
        ProductOptionValue value = valueRepository.findById(valueId)
                .filter(v -> v.getOption().getId().equals(optionId))
                .orElseThrow(ProductOptionValueNotFoundException::new);

        Map<Long, Long> counts = countVariantsByValueIds(List.of(valueId));
        int variantCount = counts.getOrDefault(valueId, 0L).intValue();
        if (variantCount > 0) {
            throw new OptionValueInUseException(variantCount);
        }

        // Defensive: ensure no variant still references this value.
        productVariantRepository.removeValueFromVariants(valueId);

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

    @Transactional
    public ProductOptionValueResponseDashboard patchValue(Long optionId, Long valueId, ProductOptionValuePatchRequest request) {
        ProductOptionValue value = valueRepository.findById(valueId)
                .filter(v -> v.getOption().getId().equals(optionId))
                .orElseThrow(ProductOptionValueNotFoundException::new);

        List<ApiFieldError> errors = new ArrayList<>();
        if (request.getNameEn() != null && request.getNameEn().trim().isEmpty()) {
            errors.add(ApiFieldError.builder().field("nameEn").message("must not be blank").build());
        }
        if (request.getNameAr() != null && request.getNameAr().trim().isEmpty()) {
            errors.add(ApiFieldError.builder().field("nameAr").message("must not be blank").build());
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        // Uniqueness checks
        if (request.getNameEn() != null) {
            String trimmed = request.getNameEn().trim();
            for (ProductOptionValue other : value.getOption().getValues()) {
                if (other.getId().equals(valueId)) continue;
                String otherName = other.getTranslations().stream()
                        .filter(t -> t.getLanguage().equals("en"))
                        .findFirst()
                        .map(ProductOptionValueTranslation::getValue)
                        .orElse(null);
                if (otherName != null && otherName.equalsIgnoreCase(trimmed)) {
                    throw new ValueExistsException();
                }
            }
        }
        if (request.getNameAr() != null) {
            String trimmed = request.getNameAr().trim();
            for (ProductOptionValue other : value.getOption().getValues()) {
                if (other.getId().equals(valueId)) continue;
                String otherName = other.getTranslations().stream()
                        .filter(t -> t.getLanguage().equals("ar"))
                        .findFirst()
                        .map(ProductOptionValueTranslation::getValue)
                        .orElse(null);
                if (otherName != null && otherName.equalsIgnoreCase(trimmed)) {
                    throw new ValueExistsException();
                }
            }
        }

        if (request.getNameEn() != null) {
            String trimmed = request.getNameEn().trim();
            valueTranslationRepository.findByOptionValueIdAndLanguage(valueId, "en")
                    .ifPresentOrElse(existing -> {
                        existing.setValue(trimmed);
                        valueTranslationRepository.save(existing);
                    }, () -> valueTranslationRepository.save(
                            ProductOptionValueTranslation.builder()
                                    .optionValue(value)
                                    .language("en")
                                    .value(trimmed)
                                    .build()));
        }
        if (request.getNameAr() != null) {
            String trimmed = request.getNameAr().trim();
            valueTranslationRepository.findByOptionValueIdAndLanguage(valueId, "ar")
                    .ifPresentOrElse(existing -> {
                        existing.setValue(trimmed);
                        valueTranslationRepository.save(existing);
                    }, () -> valueTranslationRepository.save(
                            ProductOptionValueTranslation.builder()
                                    .optionValue(value)
                                    .language("ar")
                                    .value(trimmed)
                                    .build()));
        }

        if (request.getNumericValue() != null) {
            value.setNumericValue(request.getNumericValue());
        }
        if (request.getSortOrder() != null) {
            value.setSortOrder(request.getSortOrder());
        }

        valueRepository.save(value);
        return buildValueDashboardResponse(value);
    }

    @Transactional
    public void reorderValues(Long optionId, List<Long> valueIds) {
        ProductOption option = optionRepository.findById(optionId)
                .orElseThrow(ProductOptionNotFoundException::new);

        Set<Long> owned = option.getValues().stream().map(ProductOptionValue::getId).collect(Collectors.toSet());
        for (Long id : valueIds) {
            if (!owned.contains(id)) {
                throw new ProductOptionValueNotFoundException();
            }
        }

        // Reject duplicates
        if (valueIds.size() != new HashSet<>(valueIds).size()) {
            throw new ProductOptionValueNotFoundException();
        }

        for (int i = 0; i < valueIds.size(); i++) {
            Long id = valueIds.get(i);
            ProductOptionValue v = valueRepository.findById(id).orElseThrow(ProductOptionValueNotFoundException::new);
            v.setSortOrder(i);
            valueRepository.save(v);
        }
    }

    private Map<Long, Long> countVariantsByOptionIds(List<Long> optionIds) {
        if (optionIds == null || optionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : productVariantRepository.countVariantsByOptionIds(optionIds)) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }

    private Map<Long, Long> countVariantsByValueIds(List<Long> valueIds) {
        if (valueIds == null || valueIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : productVariantRepository.countVariantsByValueIds(valueIds)) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }

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
                .appliesTo(option.getAppliesTo())
                .build();
    }

    private ProductOptionResponseDashboard buildDashboardResponse(ProductOption option) {
        return ProductOptionResponseDashboard.builder()
                .id(option.getId())
                .slug(option.getSlug())
                .sortOrder(option.getSortOrder())
                .appliesTo(option.getAppliesTo())
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