package com.nocteon.nocteon_api.product.dto.request;

import java.math.BigDecimal;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.product.enums.ProductType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductFilterRequest extends BaseFilterRequest {
    private String search;
    private String categorySlug;
    private String originSlug;
    private String farmSlug;
    private Double minRating;
    private ProductType productType;
    private Boolean featured;
    private Boolean isActive;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
}