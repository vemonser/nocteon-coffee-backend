package com.nocteon.nocteon_api.product.dto.response;

import java.time.Instant;
import java.util.List;

import com.nocteon.nocteon_api.common.dto.TranslationResponse;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import com.nocteon.nocteon_api.product.enums.ProductType;

@Getter
@Setter
@Builder
public class ProductOptionResponseDashboard {
    private Long id;
    private String slug;
    private Integer sortOrder;
    private Instant createdAt;
    private List<TranslationResponse> translations;
    private List<ProductOptionValueResponseDashboard> values;
    private Integer usedByVariants;
    private java.util.Set<ProductType> appliesTo;
}