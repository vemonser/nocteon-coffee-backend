package com.nocteon.nocteon_api.product.dto.response;

import java.math.BigDecimal;

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
    private BigDecimal weight;
    private String grindType;
    private int stock;
    private BigDecimal discount;
    private boolean isActive;
}