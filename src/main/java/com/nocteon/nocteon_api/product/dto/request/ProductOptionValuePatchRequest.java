package com.nocteon.nocteon_api.product.dto.request;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductOptionValuePatchRequest {
    private String nameEn;
    private String nameAr;
    private BigDecimal numericValue;
    private Integer sortOrder;
}