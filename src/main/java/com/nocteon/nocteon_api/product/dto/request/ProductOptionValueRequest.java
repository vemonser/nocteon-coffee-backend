package com.nocteon.nocteon_api.product.dto.request;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductOptionValueRequest {
    @NotEmpty(message = "{validation.translations.notEmpty}")
    @Size(min = 2, message = "{validation.translations.size}")
    private List<@Valid ValueTranslationRequest> translations;

    private Integer sortOrder;
    private BigDecimal numericValue;
}