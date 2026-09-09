package com.nocteon.nocteon_api.product.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.nocteon.nocteon_api.common.dto.TranslationResponse;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProductOptionValueResponseDashboard {
    private Long id;
    private String slug;
    private Integer sortOrder;
    private BigDecimal numericValue;
    private List<TranslationResponse> translations;
}