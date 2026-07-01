package com.nocteon.nocteon_api.product.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nocteon.nocteon_api.product.enums.ProductType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DashboardProductResponse {
    private Long id;
    private String slug;
    private String categorySlug;
    private String originSlug;
    private String farmSlug;
    private String roastLevelSlug;
    private ProductType productType;
    private boolean featured;
    @JsonProperty("isActive")
    private boolean isActive;

    private String name;
    private String shortDescription;
    private String description;
    private String imageUrl;
    private String primaryImageUrl;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal lowestPrice;

    private List<ProductTranslationResponse> translations;
    private DashboardCoffeeDetailsResponse coffeeDetails;
    private List<ProductVariantResponse> variants;
    private List<ProductMediaResponse> media;
    private List<String> tastingNotes;
    private List<String> pairings;
    private List<ProductBrewingMethodResponse> brewingMethods;
}
