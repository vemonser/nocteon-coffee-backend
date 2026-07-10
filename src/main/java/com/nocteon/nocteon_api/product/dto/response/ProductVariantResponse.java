package com.nocteon.nocteon_api.product.dto.response;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nocteon.nocteon_api.product.enums.GrindType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProductVariantResponse {
    private Long id;
    private String sku;
    private BigDecimal price;
    private BigDecimal weightGrams;
    private BigDecimal compareAtPrice;
    private GrindType grindType;
    private int stockQuantity;
    private Integer discountPercentage;
    @JsonProperty("isActive")
    private boolean isActive;
}
