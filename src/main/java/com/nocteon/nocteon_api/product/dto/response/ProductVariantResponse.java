package com.nocteon.nocteon_api.product.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.nocteon.nocteon_api.product.dto.response.summary.ProductOptionValueSummary;


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
    private BigDecimal compareAtPrice;
    private BigDecimal costPrice;
    private int stockQuantity;
    private int reservedQuantity;
    private int reorderThreshold;
    private Integer discountPercentage;
    private boolean isActive;
    private List<ProductOptionValueSummary> optionValues;
}