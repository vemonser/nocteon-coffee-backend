package com.nocteon.nocteon_api.product.dto.response;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nocteon.nocteon_api.product.enums.ProductType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCardResponse {

    private Long id;
    private String slug;

    private String categorySlug;

    private ProductType productType;

    private boolean featured;
    @JsonProperty("isActive")
    private boolean isActive;

    private String name;
    private String shortDescription;

    private String primaryImageUrl;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer discountPercentage;

}