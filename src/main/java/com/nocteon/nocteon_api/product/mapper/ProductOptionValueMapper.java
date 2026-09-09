package com.nocteon.nocteon_api.product.mapper;

import java.util.List;

import com.nocteon.nocteon_api.product.dto.response.summary.ProductOptionValueSummary;
import com.nocteon.nocteon_api.product.entity.ProductOptionTranslation;
import com.nocteon.nocteon_api.product.entity.ProductOptionValueTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;

public class ProductOptionValueMapper {

    public static List<ProductOptionValueSummary> buildSummaries(ProductVariant variant, String language) {
        return variant.getOptionValues().stream()
                .map(ov -> {
                    String optionName = ov.getOption().getTranslations().stream()
                            .filter(t -> t.getLanguage().equals(language))
                            .findFirst()
                            .map(ProductOptionTranslation::getName)
                            .orElse(ov.getOption().getSlug());

                    String value = ov.getTranslations().stream()
                            .filter(t -> t.getLanguage().equals(language))
                            .findFirst()
                            .map(ProductOptionValueTranslation::getValue)
                            .orElse(ov.getSlug());

                    return ProductOptionValueSummary.builder()
                            .optionSlug(ov.getOption().getSlug())
                            .optionName(optionName)
                            .valueSlug(ov.getSlug())
                            .value(value)
                            .build();
                })
                .toList();
    }
}