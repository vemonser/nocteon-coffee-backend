package com.nocteon.nocteon_api.product.dto.request;

import java.util.List;

import com.nocteon.nocteon_api.product.enums.ProductType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductRequest {

    @NotBlank(message = "{validation.category.notBlank}")
    private String categorySlug;

    private String originSlug;
    private String farmSlug;

    @NotNull(message = "{validation.productType.notNull}")
    private ProductType productType;

    private boolean featured = false;

    private boolean isActive = true;

    @NotEmpty(message = "{validation.translations.notEmpty}")
    @Size(min = 2, message = "{validation.translations.size}")
    private List<@Valid ProductTranslationRequest> translations;

    private CoffeeDetailsRequest coffeeDetails;

    @NotEmpty(message = "{validation.variants.notEmpty}")
    private List<@Valid ProductVariantRequest> variants;
    
    private List<ProductMediaRequest> media;
    
    private List<String> tastingNoteSlugs;
    private List<String> pairingSlugs;
    private List<ProductBrewingMethodRequest> brewingMethods;
}